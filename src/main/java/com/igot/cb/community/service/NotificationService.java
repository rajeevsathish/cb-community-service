package com.igot.cb.community.service;

import java.util.List;

public interface NotificationService {

  void sendNotification(List<String> moderatorIds, String communityId, String userId, String communityName);
}
