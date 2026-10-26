# AWS cost estimate for the Student Task Manager demo

**Estimate date:** 25 October 2026  
**Region:** Asia Pacific (Sydney), ap-southeast-2  
**Currency:** USD, before tax and credits  
**Basis:** on-demand public list-price estimate, using 730 hours per month

This is a model of the Terraform configuration, not a readout of the AWS bill. AWS CLI identity is currently verified, but Cost Explorer was not queried; actual usage, remaining credits, and account-specific free-plan eligibility remain unverified. Prices and free-plan benefits can change; confirm the current estimate in the AWS Pricing Calculator before relying on it.

## Estimated monthly resources

| Resource | Assumption | Estimated monthly cost |
|---|---:|---:|
| EC2 t3.small K3s host | 730 h × $0.0264/h | $19.27 |
| EC2 gp3 root disk | 30 GB × $0.096/GB-month | $2.88 |
| Public IPv4 | 730 h × $0.005/h | $3.65 |
| RDS MySQL db.t4g.micro, Single-AZ | 730 h × $0.025/h | $18.25 |
| RDS gp3 storage | 20 GB × $0.138/GB-month | $2.76 |
| Secrets Manager | One database secret | $0.40 |
| **Core estimate** |  | **$47.21/month** |
| ECR image storage | Assumption: 1 GB retained × $0.10/GB-month | $0.10 |
| **Estimate including ECR assumption** |  | **$47.31/month** |

The calculation is reproducible with the script at tools/estimate_aws_cost.py. Rates are rounded public list-price inputs. The regional AWS offer files and service pricing pages are the primary references; pricing calculators should be used to refresh them before spending decisions.

The ECR line assumes 1 GB of retained image data. Actual ECR charges depend on stored image size and any applicable account allowance. The estimate excludes data transfer beyond any free allowance, RDS backup/snapshot storage beyond included amounts, database CPU-credit surplus, extra API requests to Secrets Manager, taxes, support, and other account resources. RDS T4g runs in Unlimited mode and can incur CPU-credit charges above its baseline. AWS states that CPU credits are $0.075 per vCPU-hour for RDS T3/T4g.

## What autoscaling changes in this project

The deployed HPA scales API pods on the same EC2 host. Moving between one and two pods changes neither the EC2 instance hours nor the RDS provisioned hours, so the modeled AWS infrastructure bill changes by approximately **$0** for that pod-only scale event. This project demonstrates elasticity and recovery at the Kubernetes pod layer; it does not demonstrate lower EC2/RDS charges from pod scale-down.

A hypothetical future worker-node scenario illustrates the distinction: an additional t3.small worker kept for all 730 hours has about $19.27/month of compute cost. If it were needed for 20% of the month (146 hours), its modeled compute would be about $3.85, a compute-only difference of about $15.42/month. This is a projection, not implemented or measured. It excludes storage, networking, bootstrap/termination overhead, and the cost of building a reliable multi-node cluster.

## Credits and cost controls

A credit balance can offset eligible charges but does not change the underlying list price. Do not treat the $100 promotional credit mentioned by the account owner as a promise of zero charges: eligibility, remaining balance, taxes, and other resources must be checked in the AWS billing console. Stop or destroy demo resources after evaluation once the team has confirmed that data and evidence are no longer needed. Follow the Terraform destroy review instructions in infra/README.md; database deletion in this demo skips a final snapshot.

## Pricing references

- [AWS EC2 T3 instance details and pricing](https://aws.amazon.com/ec2/instance-types/t3/) (instance specifications; regional offer file: [EC2 Sydney price list](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/ap-southeast-2/index.csv)).
- [AWS RDS for MySQL pricing](https://aws.amazon.com/rds/mysql/pricing/) (instance hours, storage, backups, and T4g/T3 CPU-credit charges; regional offer file: [RDS Sydney price list](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonRDS/current/ap-southeast-2/index.json)).
- [AWS EBS pricing](https://aws.amazon.com/ebs/pricing/) (gp3 storage is charged by provisioned GB-month; regional offer file: [EC2/EBS Sydney price list](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/ap-southeast-2/index.csv)).
- [AWS public IPv4 pricing announcement](https://aws.amazon.com/blogs/aws/new-aws-public-ipv4-address-charge-public-ip-insights/) ($0.005 per IPv4 address-hour).
- [AWS Secrets Manager pricing](https://aws.amazon.com/secrets-manager/pricing/) ($0.40 per secret-month, with request charges above included levels).
- [AWS ECR pricing](https://aws.amazon.com/ecr/pricing/) (storage and data-transfer pricing).
- [AWS Pricing Calculator](https://calculator.aws/) (refresh the estimate with the region and actual resource usage).
