package com.example.chat.service;

import com.example.chat.domain.Conversation;
import com.example.chat.domain.ConversationType;
import com.example.chat.domain.User;
import com.example.chat.repository.ConversationRepository;
import com.example.chat.repository.MembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RoomService {

    public record RoomView(Long id, String name, List<String> members) {
    }

    private final ConversationRepository conversations;
    private final MembershipRepository memberships;
    private final UserService users;

    public RoomService(ConversationRepository conversations, MembershipRepository memberships, UserService users) {
        this.conversations = conversations;
        this.memberships = memberships;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<RoomView> listRooms() {
        Map<Long, List<String>> membersByRoom = memberships.findAllByConversationTypeWithUser(ConversationType.GROUP)
                .stream()
                .collect(Collectors.groupingBy(m -> m.getConversation().getId(),
                        Collectors.mapping(m -> m.getUser().getUsername(), Collectors.toList())));
        return conversations.findByTypeOrderByName(ConversationType.GROUP).stream()
                .map(c -> new RoomView(c.getId(), c.getName(), membersByRoom.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    /** Joining a room you are already in is a no-op. Unknown usernames are created. */
    @Transactional
    public void join(Long roomId, String username) {
        Conversation room = findRoom(roomId);
        User user = users.getOrCreate(username);
        memberships.insertIfAbsent(room.getId(), user.getId());
    }

    /** Leaving a room you are not in (or as an unknown user) is a no-op. */
    @Transactional
    public void leave(Long roomId, String username) {
        Conversation room = findRoom(roomId);
        if (users.find(username).isPresent()) {
            memberships.deleteByConversationIdAndUsername(room.getId(), username);
        }
    }

    private Conversation findRoom(Long roomId) {
        return conversations.findByIdAndType(roomId, ConversationType.GROUP)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room " + roomId + " not found"));
    }
}
