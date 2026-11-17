# Extra maven arguments, e.g. MVN_ARGS="-Dfmt.action=validate -Dpom.fmt.action=verify"
# to check (and fail on) source and pom formatting instead of rewriting the files (used by the CI)
MVN_ARGS ?=

all: install test docker

install:
	./mvnw clean install -ntp -DskipTests $(MVN_ARGS)

test:
	./mvnw verify -ntp $(MVN_ARGS)

docker:
	./mvnw install -ntp -DskipTests -pl modules/org-resolvers,modules/sirene,modules/logging -am
	@TAG=`./mvnw -f gateway/ help:evaluate -q -DforceStdout -Dexpression=imageTag` && \
	./mvnw package -f gateway/ -Pdocker -ntp -DskipTests && \
	echo tagging georchestra/gateway:$${TAG} as georchestra/gateway:latest && \
	docker tag georchestra/gateway:$${TAG} georchestra/gateway:latest && \
	docker images|grep "georchestra/gateway"|grep latest

docker-debug:
	./mvnw install -ntp -DskipTests -pl modules/org-resolvers,modules/sirene,modules/logging -am
	@TAG=`./mvnw -f gateway/ help:evaluate -q -DforceStdout -Dexpression=imageTag` && \
	./mvnw package -f gateway/ -Pdocker,docker-debug -ntp -DskipTests && \
	docker tag georchestra/gateway:$${TAG}-debug georchestra/gateway:latest-debug

deb: install
	./mvnw package deb:package -f gateway/ -PdebianPackage
