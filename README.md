# cloud-qe

## Running tests through your IDE
File > Import Existing Maven Projects and choose all sub-projects under cloud-qe.

Depending on which IDE you are using, there are a few options to set up the environment.

If you are using Eclipse or Sprint Boot Suite (STS), you could change cloud-qe/pom.xml to point to the desired environment.
e.g <test.env>int</test.env>

You might also have to add the following proxy params to your run configuration if running from within GE network:
-Dhttp.proxyHost=proxy-src.research.ge.com -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy-src.research.ge.com
-Dhttps.proxyPort=8080 -DCLIENT_SECRET=\<secret> -DTENANT_MGR_CLIENT_SECRET=\<tenant-mgr-secret>

If you are using IntelliJ, edit the em-tools/em-tools/src/main/java/com/ge/dspmicro/qe/tools/environment/Configuration file to add the CLIENT_SECRET: System.setProperty("CLIENT_SECRET",\<secret>);

Once environment setting is good, choose one of the tests you would like to run e.g em-device > CertEnrollmentTest and right click Run As TestNG Test.
It should target the desired environment when running the end to end test.

Note: please don't check in the local setting.

## Copying testPackages to local folders
Some tests need to load test packages and upload to EM-APP. So you would need to copy them to the working directory in order for test run able to find them. i.e.

\[cloud-qe feature branch]$ cp machine.zip em-app/

\[cloud-qe feature branch]$ cp configuration.zip em-api

\[cloud-qe feature branch]$ cp machine.zip em-command

\[cloud-qe feature branch]$ cp configuration.zip em-command


## Running tests on command line
Build all modules so they are saved to your local maven repo.
mvn clean install -DskipTests -B

Run the tests for a specific module
mvn -Dtest.env=int -DCLIENT_SECRET=${CLIENT_SECRET} -DTENANT_MGR_CLIENT_SECRET=${TENANT_MGR_CLIENT_SECRET} --projects :\<module> test

Choose one of the modules such as em-api, em-app, etc

If you wish to run a single test:
mvn -Dtest.env=int -DCLIENT_SECRET=${CLIENT_SECRET} -DTENANT_MGR_CLIENT_SECRET=${TENANT_MGR_CLIENT_SECRET} --projects :em-device -Dtest=CertEnrollmentTest test

Don't forget to add -Dhttp.proxyHost=proxy-src.research.ge.com -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy-src.research.ge.com -Dhttps.proxyPort=8080 if running behind GE network.


## Swagger Diff
Refer https://github.com/Sayi/swagger-diff  for com.ge.dspmicro.cloud.swaggerdiff.SwaggerCompareTest which compares swagger documents to trace backward compatibilities.Modifies SwaggerDiff.java and ParameterDiff.java

You can run swagger diff as follows:
mvn -B -DSTAGE_SWAGGER_URL=https://em-alert-apidocs-sysint.run.aws-usw02-dev.ice.predix.io/v2/api-docs -DPROD_SWAGGER_URL=https://em-alert-apidocs.run.aws-usw02-pr.ice.predix.io/v2/api-docs -Dtest=com.ge.dspmicro.cloud.swaggerdiff.SwaggerCompareTest --projects :em-swaggerdiff test

The above example is for em-alert API, you can change the endpoint depending on the service you'd like to test.
