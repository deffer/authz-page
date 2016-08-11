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

## Running (deploying)

Configure properties (following example in `src/main/resources/application.properties`) ans place it in a working folder. Run

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