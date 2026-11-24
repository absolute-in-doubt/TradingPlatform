package absolute_in_doubt.trading_platform.getaway.controller;

import absolute_in_doubt.trading_platform.getaway.dto.CommonDtos;
import absolute_in_doubt.trading_platform.getaway.dto.NotificationDtos;
import absolute_in_doubt.trading_platform.getaway.mapper.ProtoMapper;
import absolute_in_doubt.trading_platform.getaway.security.SecurityUtils;
import absolute_in_doubt.trading_platform.getaway.security.UserPrincipal;
import invest.notification.v1.*;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationServiceGrpc.NotificationServiceBlockingStub notificationServiceStub;

    @QueryMapping
    public NotificationDtos.NotificationPageDto notifications(
            @Argument(name = "unreadOnly") Boolean unreadOnly,
            @Argument(name = "page") CommonDtos.PageInput page,
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        SecurityUtils.requireUser(currentUser);

        int limit = page != null ? page.getEffectiveLimit() : 20;
        int offset = page != null ? page.getEffectiveOffset() : 0;

        ListNotificationsRequest request = ListNotificationsRequest.newBuilder()
                .setUserId(currentUser.getUserId().toString())
                .setUnreadOnly(unreadOnly != null && unreadOnly)
                .setLimit(limit)
                .setOffset(offset)
                .build();

        ListNotificationsResponse response = notificationServiceStub.listNotifications(request);
        List<NotificationDtos.NotificationDto> items = response.getItemsList().stream()
                .map(ProtoMapper::toNotificationDto)
                .toList();

        return NotificationDtos.NotificationPageDto.builder()
                .items(items)
                .totalCount(response.getTotalCount())
                .build();
    }

    @QueryMapping
    public Integer unreadNotificationsCount(
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        SecurityUtils.requireUser(currentUser);

        CountUnreadRequest request = CountUnreadRequest.newBuilder()
                .setUserId(currentUser.getUserId().toString())
                .build();

        CountUnreadResponse response = notificationServiceStub.countUnread(request);
        return response.getCount();
    }

    @MutationMapping
    public NotificationDtos.NotificationDto markNotificationRead(
            @Argument String id,
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        SecurityUtils.requireUser(currentUser);

        MarkReadRequest request = MarkReadRequest.newBuilder()
                .setNotificationId(id)
                .setUserId(currentUser.getUserId().toString())
                .build();

        NotificationResponse response = notificationServiceStub.markRead(request);
        return ProtoMapper.toNotificationDto(response);
    }

    @MutationMapping
    public Integer markAllNotificationsRead(
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        SecurityUtils.requireUser(currentUser);

        MarkAllReadRequest request = MarkAllReadRequest.newBuilder()
                .setUserId(currentUser.getUserId().toString())
                .build();

        MarkAllReadResponse response = notificationServiceStub.markAllRead(request);
        return response.getUpdatedCount();
    }
}
