#!/bin/bash

ENDPOINT_URL=${LOCALSTACK_ENDPOINT:-"http://localhost:4566"}
REGION="us-east-1"
ENVIRONMENT=${ENVIRONMENT:-"dev"}

echo "Deploying infrastructure to LocalStack"
echo "Endpoint: $ENDPOINT_URL"
echo "Environment: $ENVIRONMENT"

aws --endpoint-url=$ENDPOINT_URL cloudformation create-stack \
    --stack-name "camel-app-stack-$ENVIRONMENT" \
    --template-body file:///etc/localstack/init/ready.d/template-app.yaml \
    --parameters \
        ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
        ParameterKey=SqsQueueName,ParameterValue=event-processor-queue \
        ParameterKey=DynamoDbTableName,ParameterValue=event-messages \
        ParameterKey=EventBusName,ParameterValue=default \
    --capabilities CAPABILITY_IAM \
    --region $REGION

echo "Waiting for app stack to complete"
aws --endpoint-url=$ENDPOINT_URL cloudformation wait stack-create-complete \
    --stack-name "camel-app-stack-$ENVIRONMENT" \
    --region $REGION

aws --endpoint-url=$ENDPOINT_URL cloudformation create-stack \
    --stack-name "lambda-stack-$ENVIRONMENT" \
    --template-body file:///etc/localstack/init/ready.d/template-lambda.yaml \
    --parameters \
        ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
    --capabilities CAPABILITY_IAM \
    --region $REGION

echo "Waiting for lambda stack to complete"
aws --endpoint-url=$ENDPOINT_URL cloudformation wait stack-create-complete \
    --stack-name "lambda-stack-$ENVIRONMENT" \
    --region $REGION

echo "Infrastructure deployed successfully!"

echo ""
aws --endpoint-url=$ENDPOINT_URL cloudformation describe-stacks \
    --stack-name "camel-app-stack-$ENVIRONMENT" \
    --query "Stacks[0].Outputs" \
    --region $REGION