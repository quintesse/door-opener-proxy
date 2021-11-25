FROM jbangdev/jbang-action as build

ARG APP=app

WORKDIR /app

ADD * src/

RUN jbang build src/$APP.java ; \
    jbang export portable src/$APP.java

RUN jlink \
    --verbose \
    --add-modules \
        java.base,java.net.http \
    --compress 2 --strip-debug --no-header-files --no-man-pages \
    --output /app/java-minimal

FROM debian:buster-slim

ARG APP=app

EXPOSE 8080

WORKDIR /app

ENV APPJAR=$APP.jar

COPY --from=build /app/java-minimal ./java-minimal/
COPY --from=build /app/$APPJAR .
COPY --from=build /app/libs libs/

ENV JAVA_HOME=/app/java-minimal
ENV PATH="$PATH:$JAVA_HOME/bin"

ENTRYPOINT java -XX:+UseContainerSupport -jar $APPJAR

