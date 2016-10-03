# OAuth2 Authorization Server 

Implements a user-facing Authorization Page to support a step in OAuth2 `Implicit` and `Authorization Code` flows. Integrates with Kong, where Kong is used to manage tokens and token-based access to APIs.
The main purpose of Authorization Page is to authenticate an end user (through SSO configured on webroute) and securely communicate user's decision YES/NO to Kong.    

## Overview  

Exposes two endpoints:

 * authorize endpoint `/oauth2/authorize` which returns code or token (based on request type), part of [OAuth2 spec](https://tools.ietf.org/html/rfc6749#section-3.1)
 * user profile endpoint `/self` - a user-facing page to allow self-service token revokation

Some features specific to University include:

 * *trusted* consumers - for web application which are part of University web experience, the end user will not be asked to approve the access
 * *dynamic* consumers - common application credentials, can be used by any consumer and typically used on API Explorers. These consumers can provide an alternative `callback` uri (as long as it is on the same domain as registered in Kong)
 * something else
 

## Building

    mvn package

Releasing

    mvn release:prepare
    mvn release:perform

## Running manually

Configure properties (following example in `src/main/resources/application.properties`) and place it in a working folder. Run

    nohup java -jar authz-page-xx.jar >as.out 2>&1 &

## For developers

Start your server from Intellij IDEA as maven springboot plugin with the goal `springboot:run`

You can view the application by navigating to
http://localhost:8090/identity/oauth2/authorize?client_id=uoa-explorer-client&response_type=token

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
 


## Deployment

This application can utilize `api-installer` package to manage its initial deployment (similar to [APIs](https://wiki.auckland.ac.nz/display/AT/API+-+Production+model#API-Productionmodel-JavaAPIimplementation)).


### First install (prepare environment)

If its a new VM, run api-install script to provision user, folders and configure a new systemd service
 
    sudo api-install authserver "OAuth2 Server - University of Auckland"

Copy/update `application.properties` into `/etc/authserver/application.properties` and make sure to update connection to Kong and keep apikey empty.

### Deployment

After releasing application (which will upload into Nexus for traceability), grab the generated jar and copy it into

    /usr/share/apis/autherserver

Run the command

    sudo systemctl restart authserver

Logs can be found in `/var/log/authserver/console.log`
To change JVM properties or any other details of jar execution, look in the `/opt/authserver/authserver.initd`

## Building rpm (deprecated)
 
This application can be built into rpm, however it is not recommended. Instructions on how to build it as rpm are below.

Following general instructions on how to package an app as rpm [here](https://wiki.auckland.ac.nz/display/GroupApps/Packaging+app+as+rpm), 
place `authserver.spec` (which can be found in `deploy` folder) into `rpmbuild/SPECS` folder on your build server. Run
 
    rpmbuild -ba -D "version 0.0.8" ./SPECS/authserver.spec
    
This will download the specified version of the jar from Nexus and package it as rpm.

### Installing and running rpm

Without uploading to Uoa-apps-dev repo (because its usually not available), copy onto VM and install using yum
 
    sudo yum localinstall authserver-0.0.8-1.noarch.rpm
    
All further upgrades to Auth Server can be done by simply copying new jar version into `/usr/share/`.
Logs can be found in `/var/log/authserver` and properties in `/etc/authserver/`.

The application is installed as systemd service, to restart

    sudo systemctl restart authserver
    
To change JVM properties or any other details of jar execution, look in the `/opt/authserver/authserver.initd`