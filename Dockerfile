FROM phusion/baseimage:0.9.11
MAINTAINER carnifux <carnifux@gmail.com>
ENV DEBIAN_FRONTEND noninteractive

# Set correct environment variables.
ENV HOME /root

# Use baseimage-docker's init system.
CMD ["/sbin/my_init"]

# fix log bug
RUN update-locale

# do java shit

RUN \
  echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java8-installer && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer


# Define working directory.
WORKDIR /data

# Define commonly used JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle
ENV LANG en_US.UTF-8

RUN usermod -u 99 nobody
RUN usermod -g 100 nobody
    
ADD config.xml /config.xml
ADD rsyncMoverMover-0.1-SNAPSHOT-jar-with-dependencies.jar /rsyncMover.jar

ENTRYPOINT ["java", "-jar", "/rsyncMover.jar", "/config.xml"]