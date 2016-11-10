package org.apereo.cas.adaptors.radius.authentication;

import net.jradius.exception.TimeoutException;
import org.apache.commons.lang3.tuple.Pair;
import org.apereo.cas.adaptors.radius.RadiusServer;
import org.apereo.cas.adaptors.radius.RadiusUtils;
import org.apereo.cas.authentication.Credential;
import org.apereo.cas.authentication.HandlerResult;
import org.apereo.cas.authentication.PreventedException;
import org.apereo.cas.authentication.handler.support.AbstractPreAndPostProcessingAuthenticationHandler;
import org.apereo.cas.web.support.WebUtils;
import org.springframework.webflow.execution.RequestContext;
import org.springframework.webflow.execution.RequestContextHolder;

import javax.security.auth.login.FailedLoginException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This is {@link RadiusTokenAuthenticationHandler}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public class RadiusTokenAuthenticationHandler extends AbstractPreAndPostProcessingAuthenticationHandler {

    private List<RadiusServer> servers;
    private boolean failoverOnException;
    private boolean failoverOnAuthenticationFailure;

    /**
     * Instantiates a new Radius authentication handler.
     */
    public RadiusTokenAuthenticationHandler() {
        super();
        logger.debug("Using {}", getClass().getSimpleName());
    }

    @Override
    public boolean supports(final Credential credential) {
        return RadiusTokenCredential.class.isAssignableFrom(credential.getClass());
    }

    @Override
    protected HandlerResult doAuthentication(final Credential credential) throws GeneralSecurityException, PreventedException {
        try {
            final RadiusTokenCredential radiusCredential = (RadiusTokenCredential) credential;
            final String password = radiusCredential.getToken();

            final RequestContext context = RequestContextHolder.getRequestContext();
            final String username = WebUtils.getAuthentication(context).getPrincipal().getId();

            final Pair<Boolean, Optional<Map<String, Object>>> result =
                    RadiusUtils.authenticate(username, password, this.servers,
                            this.failoverOnAuthenticationFailure, this.failoverOnException);
            if (result.getKey()) {
                return createHandlerResult(credential, this.principalFactory.createPrincipal(username, result.getValue().get()),
                        new ArrayList<>());
            }
            throw new FailedLoginException("Radius authentication failed for user " + username);
        } catch (final Exception e) {
            throw new FailedLoginException("Radius authentication failed " + e.getMessage());
        }
    }

    /**
     * Can ping boolean.
     *
     * @return true/false
     */
    public boolean canPing() {
        final String uidPsw = getClass().getSimpleName();
        for (final RadiusServer server : this.servers) {
            logger.debug("Attempting to ping RADIUS server {} via simulating an authentication request. If the server responds "
                    + "successfully, mock authentication will fail correctly.", server);
            try {
                server.authenticate(uidPsw, uidPsw);
            } catch (final TimeoutException | SocketTimeoutException e) {

                logger.debug("Server {} is not available", server);
                continue;

            } catch (final Exception e) {
                logger.debug("Pinging RADIUS server was successful. Response {}", e.getMessage());
            }
            return true;
        }
        return false;
    }

    public List<RadiusServer> getServers() {
        return servers;
    }

    public void setServers(final List<RadiusServer> servers) {
        this.servers = servers;
    }

    public boolean isFailoverOnException() {
        return failoverOnException;
    }

    public void setFailoverOnException(final boolean failoverOnException) {
        this.failoverOnException = failoverOnException;
    }

    public boolean isFailoverOnAuthenticationFailure() {
        return failoverOnAuthenticationFailure;
    }

    public void setFailoverOnAuthenticationFailure(final boolean failoverOnAuthenticationFailure) {
        this.failoverOnAuthenticationFailure = failoverOnAuthenticationFailure;
    }
}
