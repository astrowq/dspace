/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.google;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.analytics.Analytics;
import com.google.api.services.analytics.AnalyticsScopes;
import org.apache.logging.log4j.Logger;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * User: Robin Taylor
 * Date: 11/07/2014
 * Time: 13:23
 */

public class GoogleAccount {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(GoogleAccount.class);
    // Read from config
    private final String applicationName;
    private final String tableId;
    private final String emailAddress;
    private final String certificateLocation;
    // Created from factories
    private final JsonFactory jsonFactory;

    // The Google stuff
    private Credential credential;
    private final HttpTransport httpTransport;

    private volatile static GoogleAccount uniqueInstance;
    private final Analytics client;


    private GoogleAccount() {
        applicationName = DSpaceServicesFactory.getInstance().getConfigurationService()
                                               .getProperty("google-analytics.application.name");
        tableId = DSpaceServicesFactory.getInstance().getConfigurationService()
                                       .getProperty("google-analytics.table.id");
        emailAddress = DSpaceServicesFactory.getInstance().getConfigurationService()
                                            .getProperty("google-analytics.account.email");
        certificateLocation = DSpaceServicesFactory.getInstance().getConfigurationService()
                                                   .getProperty("google-analytics.certificate.location");

        jsonFactory = JacksonFactory.getDefaultInstance();

        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            credential = authorize();
        } catch (Exception e) {
            throw new RuntimeException("Error initialising Google Analytics client", e);
        }

        // Create an Analytics instance
        client = new Analytics.Builder(httpTransport, jsonFactory, credential).setApplicationName(applicationName)
                                                                              .build();

        log.info("Google Analytics client successfully initialised");
    }

    public static GoogleAccount getInstance() {
        if (uniqueInstance == null) {
            synchronized (GoogleAccount.class) {
                if (uniqueInstance == null) {
                    uniqueInstance = new GoogleAccount();
                }
            }
        }

        return uniqueInstance;
    }

    private Credential authorize() throws Exception {
        Set<String> scopes = new HashSet<>();
        scopes.add(AnalyticsScopes.ANALYTICS);
        scopes.add(AnalyticsScopes.ANALYTICS_EDIT);
        scopes.add(AnalyticsScopes.ANALYTICS_MANAGE_USERS);
        scopes.add(AnalyticsScopes.ANALYTICS_PROVISION);
        scopes.add(AnalyticsScopes.ANALYTICS_READONLY);

        credential = new GoogleCredential.Builder()
            .setTransport(httpTransport)
            .setJsonFactory(jsonFactory)
            .setServiceAccountId(emailAddress)
            .setServiceAccountScopes(scopes)
            .setServiceAccountPrivateKeyFromP12File(new File(certificateLocation))
            .build();

        return credential;
    }


    public String getApplicationName() {
        return applicationName;
    }

    public String getTableId() {
        return tableId;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getCertificateLocation() {
        return certificateLocation;
    }

    public JsonFactory getJsonFactory() {
        return jsonFactory;
    }

    public HttpTransport getHttpTransport() {
        return httpTransport;
    }

    public Credential getCredential() {
        return credential;
    }

    public Analytics getClient() {
        return client;
    }

}

