package zone.cogni.asquare.security.saml.extension;


import org.opensaml.saml2.metadata.Company;
import org.opensaml.saml2.metadata.ContactPerson;
import org.opensaml.saml2.metadata.EmailAddress;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.GivenName;
import org.opensaml.saml2.metadata.Organization;
import org.opensaml.saml2.metadata.OrganizationDisplayName;
import org.opensaml.saml2.metadata.OrganizationName;
import org.opensaml.saml2.metadata.OrganizationURL;
import org.opensaml.saml2.metadata.SurName;
import org.opensaml.saml2.metadata.TelephoneNumber;
import org.opensaml.saml2.metadata.impl.CompanyBuilder;
import org.opensaml.saml2.metadata.impl.ContactPersonBuilder;
import org.opensaml.saml2.metadata.impl.EmailAddressBuilder;
import org.opensaml.saml2.metadata.impl.GivenNameBuilder;
import org.opensaml.saml2.metadata.impl.OrganizationBuilder;
import org.opensaml.saml2.metadata.impl.OrganizationDisplayNameBuilder;
import org.opensaml.saml2.metadata.impl.OrganizationNameBuilder;
import org.opensaml.saml2.metadata.impl.OrganizationURLBuilder;
import org.opensaml.saml2.metadata.impl.SurNameBuilder;
import org.opensaml.saml2.metadata.impl.TelephoneNumberBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.security.saml.extension.entity.CHContactPerson;
import zone.cogni.asquare.security.saml.extension.entity.CHOrganization;

/**
 * Extension of MetadataGenerator to include in SPSSO Entity Descriptor,
 * the contact person and the organisation.
 *
 * These information are requested in Entity Descriptor to be conforment with eIDAS
 *
 * @author Patrick Lezy
 * @see org.springframework.security.saml.metadata.MetadataGenerator
 */
public class MetadataGenerator extends org.springframework.security.saml.metadata.MetadataGenerator {

    CHContactPerson contactPerson = null;
    CHOrganization organisation = null;

    /**
     * Class logger.
     */
    protected static final Logger log = LoggerFactory.getLogger(org.springframework.security.saml.metadata.MetadataGenerator.class);

    /**
     * Default constructor.
     */
    public MetadataGenerator() {
        super();
    }

    /**
     * get contact person
     * @return contact person, null if not set previously
     */
    public CHContactPerson getContactPerson() {
        return contactPerson;
    }

    /**
     * set contact Person
     * @param contactPerson contact person
     */
    public void setContactPerson(CHContactPerson contactPerson) {
        this.contactPerson = contactPerson;
    }

    /**
     * get organisation
     * @return organisation, null if not set previously
     */
    public CHOrganization getOrganisation() {
        return organisation;
    }

    /**
     * set organisation
     * @param organisation organisation
     */
    public void setOrganisation(CHOrganization organisation) {
        this.organisation = organisation;
    }

    /**
     *
     */

    @Override
    public EntityDescriptor generateMetadata() {
        EntityDescriptor entityDescriptor = super.generateMetadata();

        if (contactPerson != null) {
            ContactPersonBuilder cpBldr = new ContactPersonBuilder();
            ContactPerson cp = cpBldr.buildObject();

            cp.setType(contactPerson.getType());

            CompanyBuilder cpyBldr = new CompanyBuilder();
            Company cpy = cpyBldr.buildObject();
            cpy.setName(contactPerson.getCompany());
            cp.setCompany(cpy);

            GivenNameBuilder gnBldr = new GivenNameBuilder();
            GivenName gn = gnBldr.buildObject();
            gn.setName(contactPerson.getGivenName());
            cp.setGivenName(gn);

            SurNameBuilder snBldr = new SurNameBuilder();
            SurName sn = snBldr.buildObject();
            sn.setName(contactPerson.getSurName());
            cp.setSurName(sn);

            EmailAddressBuilder emBldr = new EmailAddressBuilder();
            EmailAddress em = emBldr.buildObject();
            em.setAddress(contactPerson.getEmailAddress());
            cp.getEmailAddresses().add(em);

            TelephoneNumberBuilder tlBldr = new TelephoneNumberBuilder();
            TelephoneNumber tl = tlBldr.buildObject();
            tl.setNumber(contactPerson.getTelephoneNumber());
            cp.getTelephoneNumbers().add(tl);

            entityDescriptor.getContactPersons().add(cp);
        }

        if (organisation != null) {
            OrganizationBuilder orgBldr = new OrganizationBuilder();
            Organization org = orgBldr.buildObject();

            OrganizationName on = new OrganizationNameBuilder().buildObject();
            on.setName(organisation.getName());
            org.getOrganizationNames().add(on);

            OrganizationDisplayName od = new OrganizationDisplayNameBuilder().buildObject();
            od.setName(organisation.getDisplayName());
            org.getDisplayNames().add(od);

            OrganizationURL ou = new OrganizationURLBuilder().buildObject();
            ou.setURL(organisation.getUrl());
            org.getURLs().add(ou);

            entityDescriptor.setOrganization(org);
        }

        return entityDescriptor;
    }
}
