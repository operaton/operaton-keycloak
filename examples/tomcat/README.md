# Installation on Tomcat
This document describes the installation of the **Keycloak Operaton Identity Provider Plugin** on a full distribution for Apache Tomcat.

## Operaton Installation on Apache Tomcat

For information on how to install Operaton on Apache Tomcat carefully read and follow the installation reference within the Camunda Docs: [https://docs.camunda.org/manual/latest/installation/full/tomcat/](https://docs.camunda.org/manual/latest/installation/full/tomcat/)

## Install the Keycloak Identity Provider Plugin

In order to install the Keycloak Identity Provider Plugin you have to download the library ``operaton-keycloak-all-x.y.z.jar`` and copy it to ``$TOMCAT_HOME/lib``.

Please be aware that you must use the provided ``*-all-x.y.z.jar`` (fat jar, packaged with the "**-all**" extension) including transitive dependencies. The additional library is available since version ``1.3.0`` and can be found e.g. on [Maven Central](https://search.maven.org/search?q=g:org.operaton.bpm.extension%20AND%20a:operaton-keycloak-all).

For the records - included dependencies are:

* org.springframework:spring-web
* org.springframework:spring-beans
* org.springframework:spring-core
* org.springframework:spring-jcl
* org.apache.httpcomponents:client5
	* org.apache.httpcomponents:core5
* com.github.ben-manes.caffeine:caffeine
	* org.checkerframework:checker-qual
	* com.google.errorprone:error_prone_annotations

The dependencies are shaded into the ``keycloakjar`` package namespace.

## Configure the Keycloak Identity Provider Plugin

The last step is to edit the ``bpm-platform.xml`` file inside the folder ``$TOMCAT_HOME/conf`` and configure the plugin. A sample configuration looks as follows:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpm-platform xmlns="http://www.camunda.org/schema/1.0/BpmPlatform"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://www.camunda.org/schema/1.0/BpmPlatform http://www.camunda.org/schema/1.0/BpmPlatform ">
...
    <process-engine name="default"> ...
    <properties>...</properties>
    <plugins>
        ...
        <plugin>
        <class>org.operaton.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin</class>
        <properties>
            <property name="keycloakIssuerUrl">https://somehost:8443/auth/realms/camunda</property>
            <property name="keycloakAdminUrl">https://somehost:8443/auth/admin/realms/camunda</property>
            <property name="clientId">camunda-identity-service</property>
            <property name="clientSecret">42xx42xx-42xx-42xx-42xx-42xx42xx42xx</property>
            <property name="useUsernameAsOperatonUserId">true</property>
            <property name="useGroupPathAsOperatonGroupId">true</property>
            <property name="administratorGroupName">camunda-admin</property>
            <property name="disableSSLCertificateValidation">true</property>
        </properties>
        </plugin>
        ...
    </plugins>
    </process-engine>
</bpm-platform>
```

For a full documentation of all configuration properties see the documentation of the [Keycloak Identity Provider Plugin](https://github.com/camunda-community-hub/camunda-platform-7-keycloak) itself.
