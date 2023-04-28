/**
 * Copyright (c) 2023, WSO2 LLC. (https://www.wso2.com). All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.oauth.endpoint.par;

import org.wso2.carbon.identity.oauth.common.OAuth2ErrorCodes;
import org.wso2.carbon.identity.oauth.endpoint.exception.InvalidRequestException;
import org.wso2.carbon.identity.oauth.par.common.ParConstants;

import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.wso2.carbon.identity.oauth.endpoint.util.EndpointUtil.getErrorPageURL;

/**
 * Handles the validation of PAR request_uri.
 */
public class RequestValidator {

    /**
     * Validate PAR request_uri.
     *
     * @param requestUri of PAR request.
     * @throws InvalidRequestException PAR Invalid Request Exception.
     */
    public static boolean isValidRequestUri (String requestUri) throws InvalidRequestException {

        if (requestUriExists(requestUri)) {
            return true;
        }
        return false;
    }


    public static boolean requestUriExists(String requestUri) throws InvalidRequestException {
        Map<String, Map<String, String[]>> parRequests = ParRequestData.getRequests();

        if (parRequests.containsKey(requestUri)) {
            return true;
        } else {
            throw new InvalidRequestException("Invalid request URI in the authorization request.",
                    OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ErrorCodes.OAuth2SubErrorCodes.SESSION_TIME_OUT);
        }
    }

    public static String requestUriExpired(String requestUri) {

        Map<String, Long> requestTimes = ParRequestData.getRequestTimes();
        long currentTime = Calendar.getInstance(TimeZone.getTimeZone(ParConstants.UTC)).getTimeInMillis();
        long requestMade = requestTimes.get(requestUri);
        long defaultExpiryInSecs = ParConstants.EXPIRES_IN_DEFAULT_VALUE_IN_SEC;

        long duration = (currentTime - requestMade);

        if (!(TimeUnit.MILLISECONDS.toSeconds(duration) < defaultExpiryInSecs)) {
            return getErrorPageURL(OAuth2ErrorCodes.OAuth2SubErrorCodes.INVALID_CLIENT,
                    "request_uri expired", null);
        }
        return null;
    }

    public static boolean clientIdValidation(String authRequestClientId, String parRequestClientId) {

        return authRequestClientId.equals(parRequestClientId);
    }
}
