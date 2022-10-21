FROM openjdk:20

COPY target/scala-*/*.sh.bat ./

CMD exec ./*.sh.bat
