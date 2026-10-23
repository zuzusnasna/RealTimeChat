package com.realtimechat.chat.service;

import com.realtimechat.auth.entity.ChatUser;
import com.realtimechat.auth.repository.ChatUserRepository;
import com.realtimechat.chat.dto.ChatMessage;
import com.realtimechat.chat.dto.ChatMessageResponse;
import com.realtimechat.chat.entity.ChatRoom;
import com.realtimechat.chat.entity.ChatRoomMember;
import com.realtimechat.chat.repository.ChatMessageRepository;
import com.realtimechat.chat.repository.ChatRoomMemberRepository;
import com.realtimechat.chat.repository.ChatRoomRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    private static final int HISTORY_LIMIT = 100;
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatReadReceiptBroadcaster readReceiptBroadcaster;
    private final ChatUserRepository userRepository;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            ChatRoomRepository chatRoomRepository,
            ChatRoomMemberRepository memberRepository,
            ChatReadReceiptBroadcaster readReceiptBroadcaster,
            ChatUserRepository userRepository
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.memberRepository = memberRepository;
        this.readReceiptBroadcaster = readReceiptBroadcaster;
        this.userRepository = userRepository;
    }

    @Transactional
    public ChatMessageResponse saveMessage(Long roomId, String authenticatedSender, ChatMessage message) {
        long totalStart = System.nanoTime();
        String sender = authenticatedSender == null ? "" : authenticatedSender.trim();
        String content = message.content() == null ? "" : message.content().trim();

        // Measure the full transactional method lifecycle, including commit/rollback
        // performed by Spring after this method returns.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    log.info("[CHAT-PERF] roomId={} sender={} transactionCompletionMs={} transactionStatus={}",
                            roomId, sender, (System.nanoTime() - totalStart) / 1_000_000, status);
                }
            });
        }

        if (sender.isBlank() || sender.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이름은 1~50자로 입력해 주세요.");
        }
        if (content.isBlank() || content.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 1~1000자로 입력해 주세요.");
        }
        if (!memberRepository.existsByRoom_IdAndUsername(roomId, sender)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "채팅방 참여자만 메시지를 전송할 수 있습니다.");
        }

        // Lock the room row so requests handled by different app instances
        // serialize their message writes for this room.
        long lockStart = System.nanoTime();
        ChatRoom room = chatRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));
        long lockWaitMs = (System.nanoTime() - lockStart) / 1_000_000;

        long saveStart = System.nanoTime();
        com.realtimechat.chat.entity.ChatMessage saved = chatMessageRepository.save(
                new com.realtimechat.chat.entity.ChatMessage(sender, content, LocalDateTime.now(), room)
        );
        long dbSaveMs = (System.nanoTime() - saveStart) / 1_000_000;

        String displayName = userRepository.findByUsername(sender)
                .map(ChatUser::getDisplayName)
                .orElse(sender);
        ChatMessageResponse response = withUnreadCount(
                ChatMessageResponse.from(saved).withDisplayName(displayName),
                memberRepository.findByRoom_Id(roomId)
        );

        long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
        log.info("[CHAT-PERF] roomId={} sender={} lockWaitMs={} dbSaveMs={} saveMessageTotalMs={}",
                roomId, sender, lockWaitMs, dbSaveMs, totalMs);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getHistory(Long roomId, String username) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다.");
        }
        if (!memberRepository.existsByRoom_IdAndUsername(roomId, username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "채팅방 참여자만 메시지 내역을 조회할 수 있습니다.");
        }

        List<ChatRoomMember> members = memberRepository.findByRoom_Id(roomId);
        List<com.realtimechat.chat.entity.ChatMessage> history = chatMessageRepository.findByRoomIdOrderByIdDesc(
                roomId,
                PageRequest.of(0, HISTORY_LIMIT)
        );
        Map<String, String> displayNames = userRepository.findAllByUsernameIn(
                        history.stream().map(com.realtimechat.chat.entity.ChatMessage::getSender).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(ChatUser::getUsername, ChatUser::getDisplayName, (first, ignored) -> first, java.util.LinkedHashMap::new));
        return history.stream()
                .map(ChatMessageResponse::from)
                .map(message -> message.withDisplayName(displayNames.getOrDefault(message.sender(), message.sender())))
                .map(message -> withUnreadCount(message, members))
                .sorted((a, b) -> Long.compare(a.id(), b.id()))
                .toList();
    }

    @Transactional
    public Long markRoomRead(Long roomId, String username) {
        chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));
        ChatRoomMember member = memberRepository.findByRoom_IdAndUsername(roomId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "채팅방 참여자만 읽음 처리할 수 있습니다."));

        Long latestMessageId = chatMessageRepository.findByRoomIdOrderByIdDesc(
                roomId, PageRequest.of(0, 1)
        ).stream().findFirst().map(com.realtimechat.chat.entity.ChatMessage::getId).orElse(null);

        Long previousLastReadMessageId = member.getLastReadMessageId();
        boolean advanced = latestMessageId != null
                && (previousLastReadMessageId == null || latestMessageId > previousLastReadMessageId);

        member.markReadThrough(latestMessageId);
        memberRepository.save(member);
        if (advanced) {
            ReadReceiptEvent event = new ReadReceiptEvent(
                    roomId, username, previousLastReadMessageId, latestMessageId
            );
            // Publish only after the read position is committed, so subscribers
            // cannot reload history before the database reflects this receipt.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        readReceiptBroadcaster.broadcast(event);
                    }
                });
            } else {
                readReceiptBroadcaster.broadcast(event);
            }
        }
        return latestMessageId;
    }

    private ChatMessageResponse withUnreadCount(
            ChatMessageResponse message,
            List<ChatRoomMember> members
    ) {
        if (message.roomId() == null || message.id() == null) return message;
        long unread = members.stream()
                .filter(member -> !member.getUsername().equals(message.sender()))
                .filter(member -> member.getLastReadMessageId() == null
                        || member.getLastReadMessageId() < message.id())
                .count();
        return message.withUnreadCount(unread);
    }

    public record ReadReceiptEvent(
            Long roomId,
            String reader,
            Long previousLastReadMessageId,
            Long lastReadMessageId
    ) {}
}
