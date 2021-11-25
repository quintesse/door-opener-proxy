FROM jbangdev/jbang-action as build

ARG APP=app

WORKDIR /app

ADD * src/

RUN jbang build src/$APP.java ; \
    jbang export portable src/$APP.java

FROM openjdk:11-jre-slim

ARG APP=app

EXPOSE 8080

WORKDIR /app

ENV APPJAR=$APP.jar

COPY --from=build /app/$APPJAR .
COPY --from=build /app/libs libs/

ENTRYPOINT java -jar $APPJAR

