FROM amazoncorretto:25
WORKDIR /app

RUN yum install -y shadow-utils && \
    groupadd -r appgroup && useradd -r -g appgroup appuser && \
    yum clean all
USER appuser

COPY applications/app-service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
