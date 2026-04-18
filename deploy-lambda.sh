#!/bin/bash

ENDPOINT_URL=${LOCALSTACK_ENDPOINT:-"http://localhost:4566"}
REGION="us-east-1"
ENVIRONMENT=${ENVIRONMENT:-"dev"}
LAMBDA_NAME="event-notifier-$ENVIRONMENT"

echo "Building Lambda"
cd lambda
./gradlew clean build
cd ..

echo "Packaging Lambda"
mkdir -p build/lambda
cp lambda/build/libs/lambda-1.0.0.jar build/lambda/function.jar
cd build/lambda
zip -q function.zip function.jar
cd ../..

echo "Deploying Lambda to LocalStack"

aws --endpoint-url=$ENDPOINT_URL lambda create-function \
    --function-name $LAMBDA_NAME \
    --runtime java21 \
    --role arn:aws:iam::000000000000:role/lambda-ses-role-$ENVIRONMENT \
    --handler by.losik.lambda.LambdaHandler \
    --zip-file fileb://build/lambda/function.zip \
    --environment Variables="{FROM_EMAIL=noreply@example.com,TO_EMAIL=admin@example.com}" \
    --region $REGION \
    --timeout 30 \
    --memory-size 256 2>/dev/null || true

aws --endpoint-url=$ENDPOINT_URL lambda update-function-code \
    --function-name $LAMBDA_NAME \
    --zip-file fileb://build/lambda/function.zip \
    --region $REGION 2>/dev/null || true

echo "Lambda deployed successfully!"

echo "Creating EventBridge rule"

aws --endpoint-url=$ENDPOINT_URL events put-rule \
    --name "camel-to-lambda-$ENVIRONMENT" \
    --event-pattern '{"source": ["camel.sqs", "camel.retry"]}' \
    --event-bus-name "default-$ENVIRONMENT" \
    --region $REGION

aws --endpoint-url=$ENDPOINT_URL events put-targets \
    --rule "camel-to-lambda-$ENVIRONMENT" \
    --event-bus-name "default-$ENVIRONMENT" \
    --targets "[{\"Id\":\"1\",\"Arn\":\"arn:aws:lambda:$REGION:000000000000:function:$LAMBDA_NAME\"}]" \
    --region $REGION

aws --endpoint-url=$ENDPOINT_URL lambda add-permission \
    --function-name $LAMBDA_NAME \
    --statement-id "eventbridge-invoke" \
    --action "lambda:InvokeFunction" \
    --principal "events.amazonaws.com" \
    --source-arn "arn:aws:events:$REGION:000000000000:rule/camel-to-lambda-$ENVIRONMENT" \
    --region $REGION 2>/dev/null || true

echo "EventBridge rule created!"