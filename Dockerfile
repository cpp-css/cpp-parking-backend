#java docker image pulls from ubuntu 14:04
FROM java:latest
MAINTAINER css.cpp.edu@gmail.com

RUN apt-get update && \
    apt-get install -y unzip

WORKDIR /root
COPY target/universal/cpp-parking-backend-1.0-SNAPSHOT.zip /root/cpp-parking-backend-1.0-SNAPSHOT.zip
RUN unzip cpp-parking-backend-1.0-SNAPSHOT.zip && rm *.zip

CMD ["/root/cpp-parking-backend-1.0-SNAPSHOT/bin/cpp-parking-backend"]
EXPOSE 9000