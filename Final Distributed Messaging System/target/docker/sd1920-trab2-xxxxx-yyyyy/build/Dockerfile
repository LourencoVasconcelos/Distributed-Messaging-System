# base image - an image with openjdk  8
FROM nunopreguica/sd1920tpbase

# working directory inside docker image
WORKDIR /home/sd

# copy the jar created by assembly to the docker image
COPY target/*jar-with-dependencies.jar sd1920.jar

# copy the file of properties to the docker image
COPY messages.props messages.props

# copy server key (keystore.ks)
COPY server.ks server.ks

# copy client truststore (truststore.ks)
COPY truststore.ks truststore.ks
	
# run Discovery when starting the docker image
CMD ["java", "-Djavax.net.ssl.keyStore=/home/sd/server.ks, -Djavax.net.ssl.keyStorePassword=password, -Djavax.net.ssl.trustStore=/home/sd/truststore.ks, -Djavax.net.ssl.trustStorePassword=changeit -cp", "/home/sd/sd1920.jar", "sd1920.trab2.server.rest.MessageServer"]

