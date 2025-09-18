EchoChat - Distributed Secure Chat System
=========================================

Authors: Group G88
University of Adelaide - Secure Programming Assignment
WARNING: This version contains intentional vulnerabilities for educational purposes.

COMPILATION
-----------
mvn clean compile

RUNNING
-------
mvn exec:java -Dexec.args="<nodeId> <port>"

EXAMPLES
--------
mvn exec:java -Dexec.args="alice 8080"
mvn exec:java -Dexec.args="bob 8081"
mvn exec:java -Dexec.args="charlie 8082"

COMMANDS
--------
connect <host:port>
list
msg <nodeId> <message>
broadcast <message>
status
quit