FROM jbangdev/jbang-action

ARG APP=app
ENV APPJAR=$APP.jar

EXPOSE 8080

WORKDIR /app

ADD *.java src/

RUN jbang build src/$APP.java ; \
    jbang export local src/$APP.java ; \
    rm -rf src

ENTRYPOINT java -jar $APPJAR

