# Makefile

# use program from libxml2 to extract version from POM
GET_VERSION = xmllint --xpath "/*[name()='project']/*[name()='version']/text()"
VERSION != ${GET_VERSION} pom.xml

LOCAL_REPOSITORY = local-maven-repository
ARCHIVE_FILE = FreeBSD-bitmark-pay-${VERSION}-maven-repository.tar.gz
DIST_FILE = bitmark-inc-bitmark-pay-v${VERSION}_GH0.tar.gz
DIST_PREFIX = bitmark-pay-${VERSION}/

PORTS_DIST_DIR = /usr/ports/distfiles/bitmark

MVN = mvn -Dmaven.repo.local="${LOCAL_REPOSITORY}/m2" versions:use-latest-versions
RM = rm -f
CP = cp -p
TAR = tar
GIT = git

# just build and package the program
.PHONY: all
all:
	${MVN} --offline clean
	${MVN} --offline package


# erase current repo, fetch new files, tar up for use by port
.PHONY: create-maven-repository
create-maven-repository:
	${RM} -r '${LOCAL_REPOSITORY}/m2'
	${RM} '${ARCHIVE_FILE}'
	${MVN} clean verify
	${TAR} -czf '${ARCHIVE_FILE}' -C '${LOCAL_REPOSITORY}' m2

# create a git archive file for current HEAD/version
.PHONY: create-distfile
create-distfile:
	${RM} '${DIST_FILE}'
	${GIT} archive --format=tar.gz --output='${DIST_FILE}' --prefix='${DIST_PREFIX}' HEAD

# copy files into ports distfiles
.PHONY: copy-files
copy-files:
	${CP} '${ARCHIVE_FILE}' '${PORTS_DIST_DIR}/'
	${CP} '${DIST_FILE}' '${PORTS_DIST_DIR}/'
