package zone.cogni.asquare.security.saml.extension.entity;

import org.opensaml.saml2.metadata.ContactPersonTypeEnumeration;

/**
 * SAML Contact Person Entity
 *
 * @author Patrick Lezy
 * @see org.opensaml.saml2.metadata.ContactPerson
 */
public class CHContactPerson {
    private ContactPersonTypeEnumeration type = ContactPersonTypeEnumeration.TECHNICAL;
    private String company;
    private String givenName;
    private String surName;
    private String emailAddress;
    private String telephoneNumber;

  public CHContactPerson() {
  }

  public CHContactPerson(ContactPersonTypeEnumeration type, String company, String givenName, String surName, String emailAddress, String telephoneNumber) {
    this.type = type;
    this.company = company;
    this.givenName = givenName;
    this.surName = surName;
    this.emailAddress = emailAddress;
    this.telephoneNumber = telephoneNumber;
  }

  public ContactPersonTypeEnumeration getType() {
    return type;
  }

  public void setType(ContactPersonTypeEnumeration type) {
    this.type = type;
  }

  public String getCompany() {
    return company;
  }

  public void setCompany(String company) {
    this.company = company;
  }

  public String getGivenName() {
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  public String getSurName() {
    return surName;
  }

  public void setSurName(String surName) {
    this.surName = surName;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public String getTelephoneNumber() {
    return telephoneNumber;
  }

  public void setTelephoneNumber(String telephoneNumber) {
    this.telephoneNumber = telephoneNumber;
  }
}
