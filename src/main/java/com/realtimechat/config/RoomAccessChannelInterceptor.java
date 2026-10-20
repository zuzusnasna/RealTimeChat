package com.realtimechat.config;

import com.realtimechat.chat.repository.ChatRoomMemberRepository;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class RoomAccessChannelInterceptor implements ChannelInterceptor {

    private static final Pattern SEND_DESTINATION = Pattern.compile("^/app/chat/(\\d+)$");
    private static final Pattern SUBSCRIBE_DESTINATION = Pattern.compile("^/topic/rooms/(\\d+)$");

    private final ChatRoomMemberRepository memberRepository;

    public RoomAccessChannelInterceptor(ChatRoomMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command != StompCommand.SEND && command != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        Pattern pattern = command == StompCommand.SEND
                ? SEND_DESTINATION
                : SUBSCRIBE_DESTINATION;
        if (destination == null || !destination.matches(pattern.pattern())) {
            return message;
        }

        Principal principal = accessor.getUser();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        Long roomId = Long.valueOf(matcher.group(1));
        if (!memberRepository.existsByRoomIdAndUsername(roomId, principal.getName())) {
            throw new AccessDeniedException("채팅방 참여자만 메시지를 전송하거나 구독할 수 있습니다.");
        }

        return message;
    }
}
