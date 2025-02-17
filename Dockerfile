FROM openjdk:20
LABEL authors = "Felipe Mattos"
WORKDIR /app
COPY  build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar","app.jar"]

# docker build -t arpeggio-api -f Dockerfile .