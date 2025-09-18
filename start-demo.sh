#!/bin/bash

# EchoChat Demo Startup Script
echo "Starting EchoChat Demo..."

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Maven is not installed. Please install Maven first."
    exit 1
fi

# Compile the project
echo "Compiling project..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo ""
echo "==================================================="
echo "EchoChat nodes starting..."
echo "==================================================="
echo ""
echo "Instructions:"
echo "1. Wait for all nodes to start"
echo "2. In alice terminal, type: connect localhost:8081"
echo "3. In bob terminal, type: connect localhost:8080"
echo "4. Try commands like: msg bob Hello!"
echo "5. Try broadcast: broadcast Hello everyone!"
echo ""

# Start three nodes in separate terminals
echo "Starting Alice on port 8080..."
osascript -e 'tell app "Terminal" to do script "cd \"'"$(pwd)"'\" && mvn exec:java -Dexec.args=\"alice 8080\""'

sleep 2

echo "Starting Bob on port 8081..."
osascript -e 'tell app "Terminal" to do script "cd \"'"$(pwd)"'\" && mvn exec:java -Dexec.args=\"bob 8081\""'

sleep 2

echo "Starting Charlie on port 8082..."
osascript -e 'tell app "Terminal" to do script "cd \"'"$(pwd)"'\" && mvn exec:java -Dexec.args=\"charlie 8082\""'

echo ""
echo "Demo nodes started in separate terminals!"
echo "Check the new terminal windows to interact with the nodes."