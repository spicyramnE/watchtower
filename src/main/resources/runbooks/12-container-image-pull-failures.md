---
title: Investigating container image pull failures
tags: docker, registry, deployment
---
A deployment or job failing to start with an image pull error means the
runtime couldn't retrieve the specified container image.

**Common causes**
- The image tag was never pushed, or was pushed to the wrong registry/repo
- Registry authentication credentials are missing or expired on the runtime
- The image was deleted by a retention/cleanup policy
- A typo in the image reference (repo, tag, or digest)

**Steps**
1. Confirm the exact image reference used matches what was actually pushed
   by the build stage.
2. Verify the runtime environment has valid registry credentials.
3. Check the registry's retention policy - a short-lived tag or aggressive
   cleanup rule may have deleted the image before deploy ran.
4. Manually pull the image reference locally to confirm it exists and is
   reachable, isolating whether this is a registry issue or a runtime
   configuration issue.
