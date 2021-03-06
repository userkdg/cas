package org.apereo.cas.support.saml.web.idp.profile.builders.assertion;

import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.support.saml.OpenSamlConfigBean;
import org.apereo.cas.support.saml.SamlException;
import org.apereo.cas.support.saml.services.SamlRegisteredService;
import org.apereo.cas.support.saml.services.idp.metadata.SamlRegisteredServiceServiceProviderMetadataFacade;
import org.apereo.cas.support.saml.util.AbstractSaml20ObjectBuilder;
import org.apereo.cas.support.saml.web.idp.profile.builders.SamlProfileObjectBuilder;
import org.apereo.cas.support.saml.web.idp.profile.builders.enc.SamlIdPObjectSigner;
import org.apereo.cas.util.RandomUtils;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.RequestAbstractType;
import org.opensaml.saml.saml2.core.Statement;
import org.opensaml.saml.saml2.core.Subject;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * This is {@link SamlProfileSamlAssertionBuilder}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Slf4j
public class SamlProfileSamlAssertionBuilder extends AbstractSaml20ObjectBuilder implements SamlProfileObjectBuilder<Assertion> {
    private static final long serialVersionUID = -3945938960014421135L;

    @Autowired
    private CasConfigurationProperties casProperties;

    private final SamlProfileObjectBuilder<AuthnStatement> samlProfileSamlAuthNStatementBuilder;

    private final SamlProfileObjectBuilder<AttributeStatement> samlProfileSamlAttributeStatementBuilder;

    private final SamlProfileObjectBuilder<Subject> samlProfileSamlSubjectBuilder;

    private final SamlProfileObjectBuilder<Conditions> samlProfileSamlConditionsBuilder;

    private final transient SamlIdPObjectSigner samlObjectSigner;

    public SamlProfileSamlAssertionBuilder(final OpenSamlConfigBean configBean,
                                           final SamlProfileObjectBuilder<AuthnStatement> samlProfileSamlAuthNStatementBuilder,
                                           final SamlProfileObjectBuilder<AttributeStatement> samlProfileSamlAttributeStatementBuilder,
                                           final SamlProfileObjectBuilder<Subject> samlProfileSamlSubjectBuilder,
                                           final SamlProfileObjectBuilder<Conditions> samlProfileSamlConditionsBuilder,
                                           final SamlIdPObjectSigner samlObjectSigner) {
        super(configBean);
        this.samlProfileSamlAuthNStatementBuilder = samlProfileSamlAuthNStatementBuilder;
        this.samlProfileSamlAttributeStatementBuilder = samlProfileSamlAttributeStatementBuilder;
        this.samlProfileSamlSubjectBuilder = samlProfileSamlSubjectBuilder;
        this.samlProfileSamlConditionsBuilder = samlProfileSamlConditionsBuilder;
        this.samlObjectSigner = samlObjectSigner;
    }

    @Override
    public Assertion build(final RequestAbstractType authnRequest,
                           final HttpServletRequest request,
                           final HttpServletResponse response,
                           final Object casAssertion,
                           final SamlRegisteredService service,
                           final SamlRegisteredServiceServiceProviderMetadataFacade adaptor,
                           final String binding,
                           final MessageContext messageContext) throws SamlException {

        final List<Statement> statements = new ArrayList<>();
        final AuthnStatement authnStatement = this.samlProfileSamlAuthNStatementBuilder.build(authnRequest, request, response,
            casAssertion, service, adaptor, binding, messageContext);
        statements.add(authnStatement);
        final AttributeStatement attrStatement = this.samlProfileSamlAttributeStatementBuilder.build(authnRequest, request,
            response, casAssertion, service, adaptor, binding, messageContext);

        if (!attrStatement.getAttributes().isEmpty() || !attrStatement.getEncryptedAttributes().isEmpty()) {
            statements.add(attrStatement);
        }

        final String id = '_' + String.valueOf(Math.abs(RandomUtils.getNativeInstance().nextLong()));
        final Assertion assertion = newAssertion(statements, casProperties.getAuthn().getSamlIdp().getEntityId(),
            ZonedDateTime.now(ZoneOffset.UTC), id);
        assertion.setSubject(this.samlProfileSamlSubjectBuilder.build(authnRequest, request, response,
            casAssertion, service, adaptor, binding, messageContext));
        assertion.setConditions(this.samlProfileSamlConditionsBuilder.build(authnRequest,
            request, response, casAssertion, service, adaptor, binding, messageContext));
        signAssertion(assertion, request, response, service, adaptor, binding, authnRequest);
        return assertion;
    }

    /**
     * Sign assertion.
     *
     * @param assertion    the assertion
     * @param request      the request
     * @param response     the response
     * @param service      the service
     * @param adaptor      the adaptor
     * @param binding      the binding
     * @param authnRequest the authn request
     * @throws SamlException the saml exception
     */
    protected void signAssertion(final Assertion assertion,
                                 final HttpServletRequest request,
                                 final HttpServletResponse response,
                                 final SamlRegisteredService service,
                                 final SamlRegisteredServiceServiceProviderMetadataFacade adaptor,
                                 final String binding,
                                 final RequestAbstractType authnRequest) throws SamlException {

        if (service.isSignAssertions()) {
            LOGGER.debug("SAML registered service [{}] requires assertions to be signed", adaptor.getEntityId());
            samlObjectSigner.encode(assertion, service, adaptor, response, request, binding, authnRequest);
        } else {
            LOGGER.debug("SAML registered service [{}] does not require assertions to be signed", adaptor.getEntityId());
        }
    }
}
