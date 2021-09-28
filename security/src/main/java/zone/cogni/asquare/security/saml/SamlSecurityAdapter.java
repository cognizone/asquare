package zone.cogni.asquare.security.saml;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.velocity.app.VelocityEngine;
import org.opensaml.saml2.metadata.ContactPersonTypeEnumeration;
import org.opensaml.saml2.metadata.LocalizedString;
import org.opensaml.saml2.metadata.provider.MetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.saml2.metadata.provider.ResourceBackedMetadataProvider;
import org.opensaml.util.resource.ClasspathResource;
import org.opensaml.util.resource.ResourceException;
import org.opensaml.xml.parse.ParserPool;
import org.opensaml.xml.parse.StaticBasicParserPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.saml.SAMLAuthenticationProvider;
import org.springframework.security.saml.SAMLBootstrap;
import org.springframework.security.saml.SAMLEntryPoint;
import org.springframework.security.saml.SAMLLogoutFilter;
import org.springframework.security.saml.SAMLLogoutProcessingFilter;
import org.springframework.security.saml.SAMLProcessingFilter;
import org.springframework.security.saml.SAMLWebSSOHoKProcessingFilter;
import org.springframework.security.saml.context.SAMLContextProviderLB;
import org.springframework.security.saml.key.JKSKeyManager;
import org.springframework.security.saml.key.KeyManager;
import org.springframework.security.saml.log.SAMLDefaultLogger;
import org.springframework.security.saml.metadata.CachingMetadataManager;
import org.springframework.security.saml.metadata.ExtendedMetadata;
import org.springframework.security.saml.metadata.ExtendedMetadataDelegate;
import org.springframework.security.saml.metadata.MetadataDisplayFilter;
import org.springframework.security.saml.metadata.MetadataGeneratorFilter;
import org.springframework.security.saml.parser.ParserPoolHolder;
import org.springframework.security.saml.processor.HTTPArtifactBinding;
import org.springframework.security.saml.processor.HTTPPAOS11Binding;
import org.springframework.security.saml.processor.HTTPPostBinding;
import org.springframework.security.saml.processor.HTTPRedirectDeflateBinding;
import org.springframework.security.saml.processor.HTTPSOAP11Binding;
import org.springframework.security.saml.processor.SAMLBinding;
import org.springframework.security.saml.processor.SAMLProcessorImpl;
import org.springframework.security.saml.util.VelocityFactory;
import org.springframework.security.saml.websso.ArtifactResolutionProfile;
import org.springframework.security.saml.websso.ArtifactResolutionProfileImpl;
import org.springframework.security.saml.websso.SingleLogoutProfile;
import org.springframework.security.saml.websso.SingleLogoutProfileImpl;
import org.springframework.security.saml.websso.WebSSOProfile;
import org.springframework.security.saml.websso.WebSSOProfileConsumer;
import org.springframework.security.saml.websso.WebSSOProfileConsumerHoKImpl;
import org.springframework.security.saml.websso.WebSSOProfileConsumerImpl;
import org.springframework.security.saml.websso.WebSSOProfileECPImpl;
import org.springframework.security.saml.websso.WebSSOProfileImpl;
import org.springframework.security.saml.websso.WebSSOProfileOptions;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import zone.cogni.asquare.security.saml.extension.MetadataGenerator;
import zone.cogni.asquare.security.saml.extension.entity.CHContactPerson;
import zone.cogni.asquare.security.saml.extension.entity.CHOrganization;
import zone.cogni.asquare.security.saml.extension.service.RoleMappingService;
import zone.cogni.asquare.security.saml.extension.spring.SAMLUserAttributesMapping;
import zone.cogni.asquare.security.saml.extension.spring.SAMLUserDetailsServiceImpl;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

@Configuration
@Import({RoleMappingService.class, SAMLUserDetailsServiceImpl.class, SAMLUserAttributesMapping.class})
public class SamlSecurityAdapter extends WebSecurityConfigurerAdapter implements InitializingBean, DisposableBean {

  private final static Logger log = LoggerFactory.getLogger(SamlSecurityAdapter.class.getName());

  @Value("${cognizone.security.realmName:cognizone-app}")
  private String realmName;
  @Value("${saml.sp.entityBaseUrl:http://localhost}")
  private String entityBaseUrl;
  @Value("${saml.sp.entityID:Legi4CH}")
  private String entityID;
  @Value("${saml.sp.keystoreResource:classpath:/saml/myKeystore.jks}")
  private String keystoreResource;
  @Value("${saml.sp.keystorePassword:password}")
  private String keystorePassword;
  @Value("${saml.sp.keyAlias:sp-key}")
  private String keyAlias;
  @Value("${saml.sp.sloBindings:POST,Redirect}")
  private String[] sloBindings;
  @Value("${saml.sp.ssoBindings:POST,Artifact}")
  private String[] ssoBindings;
  @Value("${saml.sp.hokSsoBindings:}")
  private String[] hokSsoBindings;
  @Value("${saml.sp.nameIDs:EMAIL,TRANSIENT,PERSISTENT,UNSPECIFIED,X509_SUBJECT}")
  private String[] nameIDs;

  @Value("${saml.sp.provider.scheme:http}")
  private String providerScheme;
  @Value("${saml.sp.provider.name:localhost}")
  private String providerName;
  @Value("${saml.sp.provider.port:80}")
  private Integer providerPort;
  @Value("${saml.sp.provider.includeServerPort:false}")
  private boolean providerIncludeServerPort;

  @Value("${saml.idp.metadataFile:classpath:/saml/idp/wso2.xml}")
  private String idpMatadataFile;
  @Value("${saml.idp.alias:localhost}")
  private String idpAlias;

  @Value("${saml.sp.org.lang:en}")
  private String organisationLang;
  @Value("${saml.sp.org.name}")
  private String organisationName;
  @Value("${saml.sp.org.displayName}")
  private String organisationDisplayName;
  @Value("${saml.sp.org.url}")
  private String organisationUrl;

  @Value("${saml.sp.contact.type:TECHNICAL}")
  private String contactType;
  @Value("${saml.sp.contact.company}")
  private String contactCompany;
  @Value("${saml.sp.contact.givenName}")
  private String contactGivenName;
  @Value("${saml.sp.contact.surName}")
  private String contactSurname;
  @Value("${saml.sp.contact.emailAddress}")
  private String contactMail;
  @Value("${saml.sp.contact.phone}")
  private String contactPhone;

  @Value("${saml.logMessages:false}")
  private boolean logMessages;
  private final RequestMatcher protectedRequestMatcher;

  public SamlSecurityAdapter(@Qualifier("BasicProtectedRequestMatcher") RequestMatcher protectedRequestMatcher) {
    this.protectedRequestMatcher = protectedRequestMatcher;
  }

  @Autowired
  private ServletContext context;

  private Timer backgroundTaskTimer;
  private MultiThreadedHttpConnectionManager multiThreadedHttpConnectionManager;

  public void init() {
    this.backgroundTaskTimer = new Timer(true);
    this.multiThreadedHttpConnectionManager = new MultiThreadedHttpConnectionManager();
  }

  public void shutdown() {
    this.backgroundTaskTimer.purge();
    this.backgroundTaskTimer.cancel();
    this.multiThreadedHttpConnectionManager.shutdown();
  }

  @Autowired
  private SAMLUserDetailsServiceImpl samlUserDetailsServiceImpl;

  // Initialization of the velocity engine
  @Bean
  public VelocityEngine velocityEngine() {
    return VelocityFactory.getEngine();
  }

  // XML parser pool needed for OpenSAML parsing
  @Bean(initMethod = "initialize")
  public StaticBasicParserPool parserPool() {
    return new StaticBasicParserPool();
  }

  @Bean(name = "parserPoolHolder")
  public ParserPoolHolder parserPoolHolder() {
    return new ParserPoolHolder();
  }

  // Bindings, encoders and decoders used for creating and parsing messages
  @Bean
  public HttpClient httpClient() {
    return new HttpClient(this.multiThreadedHttpConnectionManager);
  }

  // SAML Authentication Provider responsible for validating of received SAML
  // messages
  @Bean
  public SAMLAuthenticationProvider samlAuthenticationProvider() {
    SAMLAuthenticationProvider samlAuthenticationProvider = new SAMLAuthenticationProvider();
    samlAuthenticationProvider.setUserDetails(samlUserDetailsServiceImpl);
    samlAuthenticationProvider.setForcePrincipalAsString(false);
    return samlAuthenticationProvider;
  }

  // Provider of SAML Context
  @Bean
  public SAMLContextProviderLB contextProvider() {
    SAMLContextProviderLB samlContextProviderLB = new SAMLContextProviderLB();
    samlContextProviderLB.setScheme(providerScheme);
    samlContextProviderLB.setServerName(providerName);
    samlContextProviderLB.setServerPort(providerPort);

    samlContextProviderLB.setIncludeServerPortInRequestURL(providerIncludeServerPort);
    samlContextProviderLB.setContextPath(context.getContextPath());
    return samlContextProviderLB;
  }

  // Initialization of OpenSAML library
  @Bean
  public static SAMLBootstrap samlBootstrap() {
    return new SAMLBootstrap();
  }

  // Logger for SAML messages and events
  @Bean
  public SAMLDefaultLogger samlLogger() {
    SAMLDefaultLogger logger = new SAMLDefaultLogger();
    logger.setLogAllMessages(logMessages);
    return logger;
  }

  // SAML 2.0 WebSSO Assertion Consumer
  @Bean
  public WebSSOProfileConsumer webSSOprofileConsumer() {
    return new WebSSOProfileConsumerImpl();
  }

  // SAML 2.0 Holder-of-Key WebSSO Assertion Consumer
  @Bean
  public WebSSOProfileConsumerHoKImpl hokWebSSOprofileConsumer() {
    return new WebSSOProfileConsumerHoKImpl();
  }

  // SAML 2.0 Web SSO profile
  @Bean
  public WebSSOProfile webSSOprofile() {
    return new WebSSOProfileImpl();
  }

  // SAML 2.0 Holder-of-Key Web SSO profile
  @Bean
  public WebSSOProfileConsumerHoKImpl hokWebSSOProfile() {
    return new WebSSOProfileConsumerHoKImpl();
  }

  // SAML 2.0 ECP profile
  @Bean
  public WebSSOProfileECPImpl ecpprofile() {
    return new WebSSOProfileECPImpl();
  }

  @Bean
  public SingleLogoutProfile logoutprofile() {
    return new SingleLogoutProfileImpl();
  }

  // Central storage of cryptographic keys
  @Bean
  public KeyManager keyManager() {
    log.debug("Setting up KeyManager using resource : '{}', keyAlias : '{}', password : '{}'.");
    DefaultResourceLoader loader = new DefaultResourceLoader();
    Resource storeFile = loader
      .getResource(keystoreResource);
    String storePass = keystorePassword;
    Map<String, String> passwords = new HashMap<>();
    passwords.put(keyAlias, keystorePassword);
    String defaultKey = keyAlias;
    return new JKSKeyManager(storeFile, storePass, passwords, defaultKey);
  }

  @Bean
  public WebSSOProfileOptions defaultWebSSOProfileOptions() {
    WebSSOProfileOptions webSSOProfileOptions = new WebSSOProfileOptions();
    webSSOProfileOptions.setIncludeScoping(false);
    return webSSOProfileOptions;
  }

  // Entry point to initialize authentication, default values taken from
  // properties file
  @Bean
  public SAMLEntryPoint samlEntryPoint() {
    SAMLEntryPoint samlEntryPoint = new SAMLEntryPoint();
    samlEntryPoint.setDefaultProfileOptions(defaultWebSSOProfileOptions());
    return samlEntryPoint;
  }

  // Setup advanced info about metadata
  @Bean
  public ExtendedMetadata extendedMetadata() {
    ExtendedMetadata extendedMetadata = new ExtendedMetadata();
    extendedMetadata.setIdpDiscoveryEnabled(false);
    extendedMetadata.setSigningAlgorithm("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
    extendedMetadata.setSignMetadata(false);
    extendedMetadata.setEcpEnabled(true);
    return extendedMetadata;
  }

  @Bean
  @Qualifier("default_IDP")
  public ExtendedMetadataDelegate defaultIdpExtendedMetadataProvider() throws ResourceException, MetadataProviderException {
    ClasspathResource cpRes = new ClasspathResource(idpMatadataFile);

    ResourceBackedMetadataProvider resProvider = new ResourceBackedMetadataProvider(
      backgroundTaskTimer, cpRes);
    resProvider.setParserPool(parserPool());
    ExtendedMetadata extendedMetadata = new ExtendedMetadata();
    // Add metadata if needed
    extendedMetadata.setAlias(idpAlias);
    ExtendedMetadataDelegate extendedMetadataDelegate =
      new ExtendedMetadataDelegate(resProvider, extendedMetadata);
    return extendedMetadataDelegate;
  }

  // IDP Metadata configuration - paths to metadata of IDPs in circle of trust
  // is here
  // Do no forget to call iniitalize method on providers
  @Bean
  @Qualifier("metadata")
  public CachingMetadataManager metadata() throws MetadataProviderException, ResourceException {
    List<MetadataProvider> providers = new ArrayList<>();
    /**providers.add(ssoCircleExtendedMetadataProvider());*/
    providers.add(defaultIdpExtendedMetadataProvider());
    CachingMetadataManager cachingMetadataManager = new CachingMetadataManager(providers);
    cachingMetadataManager.setDefaultIDP(idpAlias);
    return cachingMetadataManager;
  }

  // Filter automatically generates default SP metadata
  @Bean
  public MetadataGenerator metadataGenerator() {
    MetadataGenerator metadataGenerator = new MetadataGenerator();
    metadataGenerator.setEntityId(entityID);
    metadataGenerator.setExtendedMetadata(extendedMetadata());
    metadataGenerator.setIncludeDiscoveryExtension(false);
    metadataGenerator.setKeyManager(keyManager());

    metadataGenerator.setEntityBaseURL(entityBaseUrl);

    metadataGenerator.setNameID(Arrays.asList(nameIDs));

    metadataGenerator.setBindingsSLO(Arrays.asList(sloBindings));
    metadataGenerator.setBindingsSSO(Arrays.asList(ssoBindings));
    metadataGenerator.setBindingsHoKSSO(Arrays.asList(hokSsoBindings));

    if (organisationName != null) {
      if (!organisationName.isEmpty()) {
        CHOrganization org = new CHOrganization();
        org.setName(new LocalizedString(organisationName, organisationLang));
        org.setDisplayName(new LocalizedString(organisationDisplayName, organisationLang));
        org.setUrl(new LocalizedString(organisationUrl, organisationLang));

        metadataGenerator.setOrganisation(org);
      }
    }

    if (contactMail != null) {
      if (!contactMail.isEmpty()) {
        CHContactPerson cp = new CHContactPerson(ContactPersonTypeEnumeration.TECHNICAL, contactCompany, contactGivenName, contactSurname, contactMail, contactPhone);

        metadataGenerator.setContactPerson(cp);
      }
    }

    return metadataGenerator;
  }

  private AbstractAuthenticationToken token;
  // The filter is waiting for connections on URL suffixed with filterSuffix
  // and presents SP metadata there
  @Bean
  public MetadataDisplayFilter metadataDisplayFilter() {
    return new MetadataDisplayFilter();
  }

  // Handler deciding where to redirect user after successful login


  @Bean
  public SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler() {
    SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler =
      new SavedRequestAwareAuthenticationSuccessHandler();
    successRedirectHandler.setDefaultTargetUrl("/admin");
    return successRedirectHandler;
  }

  // Handler deciding where to redirect user after failed login
  @Bean
  public SimpleUrlAuthenticationFailureHandler authenticationFailureHandler() {
    SimpleUrlAuthenticationFailureHandler failureHandler =
      new SimpleUrlAuthenticationFailureHandler();
    failureHandler.setUseForward(true);
    failureHandler.setDefaultFailureUrl("/error");
    return failureHandler;
  }

  @Bean
  public SAMLWebSSOHoKProcessingFilter samlWebSSOHoKProcessingFilter() throws Exception {
    SAMLWebSSOHoKProcessingFilter samlWebSSOHoKProcessingFilter = new SAMLWebSSOHoKProcessingFilter();
    samlWebSSOHoKProcessingFilter.setAuthenticationSuccessHandler(successRedirectHandler());
    samlWebSSOHoKProcessingFilter.setAuthenticationManager(authenticationManager());
    samlWebSSOHoKProcessingFilter.setAuthenticationFailureHandler(authenticationFailureHandler());
    return samlWebSSOHoKProcessingFilter;
  }

  // Processing filter for WebSSO profile messages
  @Bean
  public SAMLProcessingFilter samlWebSSOProcessingFilter() throws Exception {
    SAMLProcessingFilter samlWebSSOProcessingFilter = new SAMLProcessingFilter();
    samlWebSSOProcessingFilter.setAuthenticationManager(authenticationManager());
    samlWebSSOProcessingFilter.setAuthenticationSuccessHandler(successRedirectHandler());
    samlWebSSOProcessingFilter.setAuthenticationFailureHandler(authenticationFailureHandler());
    return samlWebSSOProcessingFilter;
  }

  @Bean
  public MetadataGeneratorFilter metadataGeneratorFilter() {
    return new MetadataGeneratorFilter(metadataGenerator());
  }

  // Handler for successful logout
  @Bean
  public SimpleUrlLogoutSuccessHandler successLogoutHandler() {
    SimpleUrlLogoutSuccessHandler successLogoutHandler = new SimpleUrlLogoutSuccessHandler();
    successLogoutHandler.setDefaultTargetUrl("/");
    return successLogoutHandler;
  }

  // Logout handler terminating local session
  @Bean
  public SecurityContextLogoutHandler logoutHandler() {
    SecurityContextLogoutHandler logoutHandler =
      new SecurityContextLogoutHandler();
    logoutHandler.setInvalidateHttpSession(true);
    logoutHandler.setClearAuthentication(true);
    return logoutHandler;
  }

  // Filter processing incoming logout messages
  // First argument determines URL user will be redirected to after successful
  // global logout
  @Bean
  public SAMLLogoutProcessingFilter samlLogoutProcessingFilter() {
    return new SAMLLogoutProcessingFilter(successLogoutHandler(),
                                          logoutHandler());
  }

  // Overrides default logout processing filter with the one processing SAML
  // messages
  @Bean
  public SAMLLogoutFilter samlLogoutFilter() {
    return new SAMLLogoutFilter(successLogoutHandler(),
                                new LogoutHandler[]{logoutHandler()},
                                new LogoutHandler[]{logoutHandler()});
  }

  // Bindings
  private ArtifactResolutionProfile artifactResolutionProfile() {
    final ArtifactResolutionProfileImpl artifactResolutionProfile =
      new ArtifactResolutionProfileImpl(httpClient());
    artifactResolutionProfile.setProcessor(new SAMLProcessorImpl(soapBinding()));
    return artifactResolutionProfile;
  }

  @Bean
  public HTTPArtifactBinding artifactBinding(ParserPool parserPool, VelocityEngine velocityEngine) {
    return new HTTPArtifactBinding(parserPool, velocityEngine, artifactResolutionProfile());
  }

  @Bean
  public HTTPSOAP11Binding soapBinding() {
    return new HTTPSOAP11Binding(parserPool());
  }

  @Bean
  public HTTPPostBinding httpPostBinding() {
    return new HTTPPostBinding(parserPool(), velocityEngine());
  }

  @Bean
  public HTTPRedirectDeflateBinding httpRedirectDeflateBinding() {
    return new HTTPRedirectDeflateBinding(parserPool());
  }

  @Bean
  public HTTPSOAP11Binding httpSOAP11Binding() {
    return new HTTPSOAP11Binding(parserPool());
  }

  @Bean
  public HTTPPAOS11Binding httpPAOS11Binding() {
    return new HTTPPAOS11Binding(parserPool());
  }

  // Processor
  @Bean
  public SAMLProcessorImpl processor() {
    Collection<SAMLBinding> bindings = new ArrayList<SAMLBinding>();
    bindings.add(httpRedirectDeflateBinding());
    bindings.add(httpPostBinding());
    bindings.add(artifactBinding(parserPool(), velocityEngine()));
    bindings.add(httpSOAP11Binding());
    bindings.add(httpPAOS11Binding());
    return new SAMLProcessorImpl(bindings);
  }

  /**
   * Define the security filter chain in order to support SSO Auth by using SAML 2.0
   *
   * @return Filter chain proxy
   * @throws Exception
   */
  @Bean
  public FilterChainProxy samlFilter() throws Exception {
    List<SecurityFilterChain> chains = new ArrayList<SecurityFilterChain>();
    chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/login/**"),
                                              samlEntryPoint()));
    chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/logout/**"),
                                              samlLogoutFilter()));
    chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/metadata/**"),
                                              metadataDisplayFilter()));
    chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/SSO/**"),
                                              samlWebSSOProcessingFilter()));
    chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/SSOHoK/**"),
                                              samlWebSSOHoKProcessingFilter()));
    chains.add(new DefaultSecurityFilterChain(new AntPathRequestMatcher("/saml/SingleLogout/**"),
                                              samlLogoutProcessingFilter()));
    return new FilterChainProxy(chains);
  }

  /**
   * Returns the authentication manager currently used by Spring.
   * It represents a bean definition with the aim allow wiring from
   * other classes performing the Inversion of Control (IoC).
   *
   * @throws Exception
   */
  @Bean
  @Override
  public AuthenticationManager authenticationManagerBean() throws Exception {
    return super.authenticationManagerBean();
  }

  /**
   * Defines the web based security configuration.
   *
   * @param http It allows configuring web based security for specific http requests.
   * @throws Exception
   */


  @Override
  protected void configure(HttpSecurity http) throws Exception {

    http.csrf().disable();
    http
      .httpBasic()
      .realmName(realmName)
      .authenticationEntryPoint(samlEntryPoint());
    http
      .addFilterBefore(metadataGeneratorFilter(), ChannelProcessingFilter.class)
      .addFilterAfter(samlFilter(), BasicAuthenticationFilter.class)
      .addFilterBefore(samlFilter(), CsrfFilter.class);
    http
      .authorizeRequests()
      .antMatchers("/saml/**").permitAll()
      .anyRequest().authenticated();
      //.requestMatchers(protectedRequestMatcher).authenticated();

    http
      .logout().disable();    // The logout procedure is already handled by SAML filters.
  }

  /**
   * Sets a custom authentication provider.
   *
   * @param auth SecurityBuilder used to create an AuthenticationManager.
   * @throws Exception
   */
  @Override
  protected void configure(AuthenticationManagerBuilder auth) {
    auth.authenticationProvider(samlAuthenticationProvider());
  }

  @Override
  public void afterPropertiesSet() {
    init();
  }

  @Override
  public void destroy() {
    shutdown();
  }

}