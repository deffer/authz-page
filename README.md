# OAuth2 Authorization Server 

Implements a user-facing Authorization Page to support a step in OAuth2 `Imlicit` and `Authorization Code` flows. Integates with Kong which is used to manage tokens and token-based access to APIs.

## Overview  

Exposes two endpoints:

 * authorize endpoint `/oauth2/authorize` which returns code or token (based on request type), part of [OAuth2 spec](https://tools.ietf.org/html/rfc6749#section-3.1)
 * user profile endpoint `/self` - a user-facing page to allow self-service token revokation

Some features specific to University include:

 * *trusted* consumers - for web application which are part of University web expirience, the end user will not be asked to approve the access
 * *dynamic* consumers - common application credentials, can be used by any consumer and typically used on API Explorers. These consumers can provide an alternative `callback` uri (as long as it is on the same domain as registered in Kong)
 * something else
 

## Building

    mvn package

## Running manually

Configure properties (following example in `src/main/resources/application.properties`) and place it in a working folder. Run

    nohup java -jar authz-page-xx.jar >as.out 2>&1 &

## For developers

Start your server from Intellij IDEA as maven springboot plugin with the goal `springboot:run`

You can view the application by navigating to
http://localhost:8090/identity/oauth2/authorize?client_id=localhost-dev-client&response_type=token

You can change the port in application.properties.

The properties files will be looked up in:

 * application.properties in classpath
 * application.properties in the current folder
 * $HOME/.webdev/as.properties
 * $HOMEPATH/.webdev/as.properties

Please note, application.properties in the classpath take preference 
over as.properties from home folders. When building application,
ensure all properties in application.properties are commented.

Another note. Logging configuration must go into `application.properties` because for some reason if its in the additional properties (`as.properties`) it is ignored.
 
## Building rpm
 
Following general instructions on how to package an app as rpm [here](https://wiki.auckland.ac.nz/display/GroupApps/Packaging+app+as+rpm), 
place `authserver.spec` (which can be found in `deploy` folder) into `rpmbuild/SPECS` folder on your build server. Run
 
    rpmbuild -ba -D "version 0.0.8" ./SPECS/authserver.spec
    
This will download the specified version of the jar from Nexus and package it as rpm.

## Installing and running rpm

Without uploading to ooa-apps-dev repo (because its usually not available), copy onto VM and install using yum
 
    sudo yum localinstall authserver-0.0.8-1.noarch.rpm
    
All further upgrades to Auth Server can be done by simply copying new jar version into `/usr/share/authserver`.
Logs can be found in `/var/log/authserver` and properties in `/etc/authserver/`.

The application is installed as systemd service, to restart

    sudo systemctl restart authserver
    
To change JVM properties or any other details of jar execution, look in the `/opt/authserver/authserver.initd`