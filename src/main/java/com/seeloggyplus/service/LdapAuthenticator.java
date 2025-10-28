package com.seeloggyplus.service;

import com.seeloggyplus.exceptions.LdapException;
import com.unboundid.ldap.sdk.*;

public class LdapAuthenticator {
    private final String ldapHost = "10.100.0.43";
    private final int ldapPort = 389;

    private final String searchUserDN = "cn=admin,dc=jellyfish,dc=work,dc=gd";
    private final String searchUserPassword = "Se27..";

    private final String searchBaseDN = "ou=accounts,dc=jellyfish,dc=work,dc=gd";

    public LdapAuthenticator() {
    }

    public boolean authenticate(String username, String password, String country) throws LdapException {
        if (password == null || password.isEmpty()) {
            return false;
        }

        try (LDAPConnection connection = new LDAPConnection(ldapHost, ldapPort)) {
            connection.bind(searchUserDN, searchUserPassword);

            String filterFormatter = String.format("(&(uid=%s)(st=%s))", username, country);

            SearchRequest searchRequest = new SearchRequest(searchBaseDN, SearchScope.SUB, filterFormatter, "dn");
            SearchResult searchResult = connection.search(searchRequest);

            if (searchResult.getEntryCount() != 1) {
                System.err.println("User not found: " + username);
                throw new LdapException(String.format("%s Not Found", username));
            }

            String userDN = searchResult.getSearchEntries().get(0).getDN();

            BindResult bindResult = connection.bind(userDN, password);

            return bindResult.getResultCode() == ResultCode.SUCCESS;
        } catch (LDAPException e) {
            System.err.println("Gagal autentikasi LDAP: " + e.getMessage());
            throw new LdapException(e.getMessage());
        }
    }

}
