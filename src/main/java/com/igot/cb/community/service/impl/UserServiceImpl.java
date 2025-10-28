package com.igot.cb.community.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.community.service.UserService;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
  @Autowired
  CassandraOperation cassandraOperation;

  private Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

  @Autowired
  private ObjectMapper objectMapper;

    @Override
    public List<Object> fetchUserFromprimary(List<String> userIds) {
        logger.info("UserService::fetchUserFromprimary:inside");
        Map<String, Object> propertyMap = Map.of(Constants.ID, userIds);

        List<Map<String, Object>> userInfoList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_USER,
                propertyMap,
                Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID, Constants.CHANNEL),
                null);

        return userInfoList.stream()
                .map(this::buildUserMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildUserMap(Map<String, Object> userInfo) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.USER_ID_KEY, userInfo.get(Constants.ID));
        userMap.put(Constants.FIRST_NAME_KEY, userInfo.get(Constants.FIRST_NAME));
        userMap.put(Constants.DEPARTMENT, userInfo.get(Constants.CHANNEL));

        String profileDetails = (String) userInfo.get(Constants.PROFILE_DETAILS);
        if (StringUtils.isNotBlank(profileDetails)) {
            try {
                populateProfileDetails(userMap, profileDetails);
            } catch (Exception e) {
                logger.error("Exception occured while fetching newlyRegistered user's data from cassandra for communityMemberListing:", e);
                throw new CustomException(Constants.ERROR, "error while processing", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return userMap;
    }

    private void populateProfileDetails(Map<String, Object> userMap, String profileDetails) throws Exception {
        Map<String, Object> profileDetailsMap = objectMapper.readValue(profileDetails, new TypeReference<HashMap<String, Object>>() {});
        userMap.put(Constants.PROFILE_IMG_KEY, "");
        userMap.put(Constants.DESIGNATION_KEY, "");
        userMap.put(Constants.PROFILE_STATUS, "");

        if (MapUtils.isNotEmpty(profileDetailsMap)) {
            addProfileImage(userMap, profileDetailsMap);
            addProfessionalDetails(userMap, profileDetailsMap);
            addProfileStatus(userMap, profileDetailsMap);
        }
    }

    private void addProfileImage(Map<String, Object> userMap, Map<String, Object> profileDetailsMap) {
        Object image = profileDetailsMap.get(Constants.PROFILE_IMG);
        if (image instanceof String && StringUtils.isNotBlank((String) image)) {
            userMap.put(Constants.PROFILE_IMG_KEY, image);
        }
    }

    private void addProfessionalDetails(Map<String, Object> userMap, Map<String, Object> profileDetailsMap) {
        Object professionalDetailsObj = profileDetailsMap.get(Constants.PROFESSIONAL_DETAILS);
        if (professionalDetailsObj instanceof List<?> professionalDetailsList && !professionalDetailsList.isEmpty()) {
            Object first = professionalDetailsList.get(0);
            if (first instanceof Map<?, ?> firstEntry) {
                Object designationObj = firstEntry.get(Constants.DESIGNATION);
                if (designationObj instanceof String designation && StringUtils.isNotBlank(designation)) {
                    userMap.put(Constants.DESIGNATION_KEY, designation);
                }
            }
        }
    }

    private void addProfileStatus(Map<String, Object> userMap, Map<String, Object> profileDetailsMap) {
        Object status = profileDetailsMap.get(Constants.PROFILE_STATUS_KEY);
        if (status instanceof String && StringUtils.isNotBlank((String) status)) {
            userMap.put(Constants.PROFILE_STATUS, status);
        }
    }

}
