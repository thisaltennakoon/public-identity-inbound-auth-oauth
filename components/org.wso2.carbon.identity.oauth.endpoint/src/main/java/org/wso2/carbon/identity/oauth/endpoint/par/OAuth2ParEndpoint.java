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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.catalina.util.ParameterMap;
import org.apache.cxf.interceptor.InInterceptors;
import org.apache.oltu.oauth2.as.request.OAuthAuthzRequest;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.oauth.client.authn.filter.OAuthClientAuthenticatorProxy;
import org.wso2.carbon.identity.oauth.common.OAuthConstants;
import org.wso2.carbon.identity.oauth.endpoint.exception.ParErrorDTO;
import org.wso2.carbon.identity.oauth.par.common.ParConstants;
import org.wso2.carbon.identity.oauth.par.dao.CacheBackedParDAO;
import org.wso2.carbon.identity.oauth.par.dao.ParDAOFactory;
import org.wso2.carbon.identity.oauth.par.dao.ParRequest;
import org.wso2.carbon.identity.oauth.par.exceptions.ParCoreException;
import org.wso2.carbon.identity.oauth.par.model.ParAuthCodeResponse;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.OAuth2Service;
import org.wso2.carbon.identity.oauth2.dto.OAuth2ClientValidationResponseDTO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Rest implementation for OAuth2 PAR endpoint.
 */
@Path("/par")
@InInterceptors(classes = OAuthClientAuthenticatorProxy.class)
public class OAuth2ParEndpoint {

    private ParAuthResponseHandler parAuthResponseHandler = new ParAuthResponseHandler();
    private ParAuthCodeResponse parAuthCodeResponse = new ParAuthCodeResponse();
    private static OAuthAuthzRequest oAuthAuthzRequest;

    @POST
    @Path("/")
    @Consumes("application/x-www-form-urlencoded")
    @Produces("application/json")
    public Response par(@Context HttpServletRequest request, @Context HttpServletResponse response,
                        MultivaluedMap<String, String> paramMap) throws ParErrorDTO, Exception {

        long expiresIn = Calendar.getInstance(TimeZone.getTimeZone(ParConstants.UTC)).getTimeInMillis();

        OAuth2Service oAuth2Service = new OAuth2Service();
        OAuth2ClientValidationResponseDTO oAuth2ClientValidationResponseDTO = oAuth2Service.validateClientInfo(request);

        if (!oAuth2ClientValidationResponseDTO.isValidClient()) {

            return getErrorResponse(oAuth2ClientValidationResponseDTO);
        } else if (isRequestUriProvided(request.getParameterMap())) {

            return getErrorResponse(rejectRequestWithRequestUri()); // passes par error object to obtain error response
        }

        HashMap<String, String> parameters = new HashMap<>();
        for (ParameterMap.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue()[0];
            parameters.put(key, value);
        }

        // get response
        Response resp = getAuthResponse(response, parAuthCodeResponse);

        try {
            // serialize parameter to JSON String
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(parameters);

            // Store values to Database
            String reqUUID = parAuthCodeResponse.getRequestUri();
            String uuid = reqUUID.substring(reqUUID.length() - 36);
            ParDAOFactory.getInstance().getParAuthMgtDAO()
                    .persistParRequestData(uuid, parameters.get(OAuthConstants.OAuth20Params.CLIENT_ID), expiresIn);

            // Store request parameters as key value pairs
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                ParDAOFactory.getInstance().getParAuthMgtDAO()
                        .persistParRequestParams(uuid, key, value);
            }

            // Add data to cache
            CacheBackedParDAO cacheBackedParDAO = new CacheBackedParDAO();
            ParRequest parRequest = new ParRequest(reqUUID, parameters, expiresIn);
            int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
            cacheBackedParDAO.addParClaim(uuid, parRequest, tenantId);
//            System.out.println("CacheBackedParClaimDAO: " + ParCache.getInstance().getValueFromCache(uuid, tenantId));

        } catch (ParCoreException e) {
            throw new IdentityOAuth2Exception("Error occurred in persisting PAR request", e);
        }


        return resp;
    }

    /**
     * Creates PAR AuthenticationResponse.
     *
     * @param response            Authentication response object.
     * @param parAuthCodeResponse PAR Authentication Request Data Transfer Object.
     * @return Response for AuthenticationRequest.
     */
    private Response getAuthResponse(@Context HttpServletResponse response, ParAuthCodeResponse parAuthCodeResponse) {

        return parAuthResponseHandler.createAuthResponse(response, parAuthCodeResponse);
    }

    /**
     * Creates Client Authentication Error Response.
     *
     * @param oAuth2ClientValidationResponseDTO PAR Authentication Failed Exception.
     * @return response Authentication Error Responses for AuthenticationRequest.
     */
    private Response getErrorResponse(OAuth2ClientValidationResponseDTO oAuth2ClientValidationResponseDTO) {

        return parAuthResponseHandler.createErrorResponse(oAuth2ClientValidationResponseDTO);
    }

    /**
     * Creates PAR Authentication Error Response.
     *
     * @param parErrorDTO PAR Authentication Failed Exception.
     * @return response PAR Authentication Error Responses for AuthenticationRequest.
     */
    private Response getErrorResponse(ParErrorDTO parErrorDTO) {

        return parAuthResponseHandler.createErrorResponse(parErrorDTO);
    }

    private boolean isRequestUriProvided(Map<String, String[]> parameters) {

        return parameters.containsKey("request_uri");
    }

    /**
     * Creates PAR invalid request Error Response.
     *
     * @return response PAR Bad request Error Responses for AuthenticationRequest.
     */
    private ParErrorDTO rejectRequestWithRequestUri() {

        ParErrorDTO parErrorDTO = new ParErrorDTO();
        parErrorDTO.setValidClient(false);
        parErrorDTO.setErrorCode(HttpServletResponse.SC_BAD_REQUEST);
        parErrorDTO.setErrorMsg("requestUri_provided");
        return parErrorDTO;
    }
}
