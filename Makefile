# Makefile

# use program from libxml2 to extract version from POM
GET_VERSION = xmllint --xpath "/*[name()='project']/*[name()='version']/text()"
VERSION != ${GET_VERSION} pom.xml

LOCAL_REPOSITORY = local-maven-repository
ARCHIVE_FILE = FreeBSD-bitmark-pay-${VERSION}-maven-repository.tar.gz

MVN = mvn -Dmaven.repo.local="${LOCAL_REPOSITORY}/m2"
RM = rm -f
TAR = tar

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
