package com.igot.cb.community.service;

import java.util.List;

public interface UserService {

  List<Object> fetchUserFromprimary(List<String> userIds);

}
