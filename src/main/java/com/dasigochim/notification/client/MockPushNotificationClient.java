package com.dasigochim.notification.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockPushNotificationClient implements PushNotificationClient {
    @Override
    public boolean send(String pushToken, String title, String message) {
        log.info("Mock push skipped: title={}, tokenPresent={}", title, pushToken != null);
        return true;
    }
}
