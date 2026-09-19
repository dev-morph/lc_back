#!/usr/bin/env python3
"""Run in authenticated AWS CloudShell. Review the plan before using --apply."""
import argparse
import json
import boto3

ACCOUNT = '264284393033'
REGION = 'ap-northeast-2'
INSTANCE = 'i-004dfd68aefd00c4e'
INSTANCE_ROLE = 'oao-api-ses-role'
ROLE = 'oao-github-deploy-role'
DOCUMENT = 'OaoDeployBackend'
PROVIDER = f'arn:aws:iam::{ACCOUNT}:oidc-provider/token.actions.githubusercontent.com'
TRUST = {'Version': '2012-10-17', 'Statement': [{
    'Effect': 'Allow', 'Principal': {'Federated': PROVIDER},
    'Action': 'sts:AssumeRoleWithWebIdentity',
    'Condition': {'StringEquals': {
        'token.actions.githubusercontent.com:aud': 'sts.amazonaws.com',
        'token.actions.githubusercontent.com:sub': 'repo:dev-morph/lc_back:ref:refs/heads/main',
    }},
}]}
POLICY = {'Version': '2012-10-17', 'Statement': [
    {'Effect': 'Allow', 'Action': 'ssm:SendCommand', 'Resource': [
        f'arn:aws:ssm:{REGION}:{ACCOUNT}:document/{DOCUMENT}',
        f'arn:aws:ec2:{REGION}:{ACCOUNT}:instance/{INSTANCE}',
    ]},
    {'Effect': 'Allow', 'Action': 'ssm:GetCommandInvocation', 'Resource': '*'},
]}
DEPLOY = """set -euo pipefail
cd /home/ubuntu/workdir/lc_back
exec 9>/home/ubuntu/.oao-backend-deploy.lock
flock -w 1800 9
git fetch origin main
commit="{{ Commit }}"
git merge-base --is-ancestor "$commit" origin/main
deploy_script=$(mktemp)
trap 'rm -f "$deploy_script"' EXIT
git show "$commit:deploy/remote-deploy.sh" > "$deploy_script"
DEPLOY_REVISION="$commit" bash "$deploy_script"
"""
CONTENT = {
    'schemaVersion': '2.2',
    'description': 'Deploy a tested lc_back main commit with mandatory backup and health check.',
    'parameters': {'Commit': {'type': 'String', 'allowedPattern': '^[0-9a-f]{40}$'}},
    'mainSteps': [{'action': 'aws:runShellScript', 'name': 'deployBackend', 'inputs': {
        'timeoutSeconds': '1800',
        'runCommand': ["runuser -u ubuntu -- bash <<'OAO_DEPLOY'\n" + DEPLOY + 'OAO_DEPLOY'],
    }}],
}


def main():
    args = argparse.ArgumentParser()
    args.add_argument('--apply', action='store_true')
    apply = args.parse_args().apply
    session = boto3.Session(region_name=REGION)
    if session.client('sts').get_caller_identity()['Account'] != ACCOUNT:
        raise SystemExit('Wrong AWS account; no changes made.')
    print(json.dumps({'role': ROLE, 'trust': TRUST, 'policy': POLICY,
                     'instanceRole': INSTANCE_ROLE, 'instance': INSTANCE,
                     'document': DOCUMENT, 'apply': apply}, indent=2))
    if not apply:
        return
    iam, ssm = session.client('iam'), session.client('ssm')
    try:
        existing = iam.get_open_id_connect_provider(OpenIDConnectProviderArn=PROVIDER)
        if 'sts.amazonaws.com' not in existing['ClientIDList']:
            raise SystemExit('Existing GitHub provider lacks STS audience; review manually.')
    except iam.exceptions.NoSuchEntityException:
        iam.create_open_id_connect_provider(
            Url='https://token.actions.githubusercontent.com', ClientIDList=['sts.amazonaws.com'])
    try:
        iam.get_role(RoleName=ROLE)
    except iam.exceptions.NoSuchEntityException:
        iam.create_role(RoleName=ROLE, AssumeRolePolicyDocument=json.dumps(TRUST),
                        Description='lc_back main deployment through one SSM document')
    else:
        iam.update_assume_role_policy(RoleName=ROLE, PolicyDocument=json.dumps(TRUST))
    iam.put_role_policy(RoleName=ROLE, PolicyName='OaoBackendDeploy', PolicyDocument=json.dumps(POLICY))
    iam.attach_role_policy(RoleName=INSTANCE_ROLE,
                           PolicyArn='arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore')
    try:
        ssm.create_document(Name=DOCUMENT, DocumentType='Command', DocumentFormat='JSON',
                            Content=json.dumps(CONTENT))
    except ssm.exceptions.DocumentAlreadyExists:
        try:
            result = ssm.update_document(Name=DOCUMENT, DocumentVersion='$LATEST',
                                          DocumentFormat='JSON', Content=json.dumps(CONTENT))
            ssm.update_document_default_version(Name=DOCUMENT,
                DocumentVersion=result['DocumentDescription']['DocumentVersion'])
        except ssm.exceptions.DuplicateDocumentContent:
            pass
    print('Configured role ARN: ' + f'arn:aws:iam::{ACCOUNT}:role/{ROLE}')
    print('SSM managed instance:', ssm.describe_instance_information(
        Filters=[{'Key': 'InstanceIds', 'Values': [INSTANCE]}])['InstanceInformationList'])


if __name__ == '__main__':
    main()
