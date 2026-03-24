#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"

timeout 120 mvn -q -DskipTests compile
timeout 120 mvn -q -Dtest=AgentConfigBindingTest,AgentInfoContributorTest,AppConfigRuntimePathTest,KnowledgeBaseConfigBindingTest,AppConfigModelSelectionTest,AppConfigVectorStoreSelectionTest,HttpModelServiceTest,HuaweiCloudApiCrawlerServiceTest test
timeout 180 mvn -q -DskipTests package
