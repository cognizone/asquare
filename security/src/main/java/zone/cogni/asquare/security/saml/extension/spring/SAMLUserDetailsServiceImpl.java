package zone.cogni.asquare.security.saml.extension.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.security.saml.userdetails.SAMLUserDetailsService;
import org.springframework.stereotype.Service;
import zone.cogni.asquare.security.saml.extension.service.RoleMappingService;

@Service
public class SAMLUserDetailsServiceImpl implements SAMLUserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(SAMLUserDetailsServiceImpl.class);

    @Autowired
    private SAMLUserAttributesMapping czsamlUserAttributesMapping;

    @Autowired
    private RoleMappingService roleMappingService;

    public Object loadUserBySAML(SAMLCredential samlCredential)
            throws UsernameNotFoundException {
        // The method is supposed to identify local account of user referenced by
        // data in the SAML assertion and return UserDetails object describing the user.

        // In a real scenario, this implementation has to locate user in a arbitrary
        // dataStore based on information present in the SAMLCredential and
        // returns such a date in a form of application specific UserDetails object.
        return new SAMLUserDetails(samlCredential, czsamlUserAttributesMapping, roleMappingService);
    }
}
