# Go v1.0 signing producer

The dedicated public Go repository currently lacks the existing signing secrets.
This workflow builds only reviewed immutable Go source
36323abbebfa0d02b069b4f23cb502607f65e7c0, reruns application tests/lint and signs
inside the existing TeleRC signing environment. It does not export the key.
The signed APK must match the prior Go application's ID and signer and have an
increasing Android version code before any artifact is accepted.

Go source and signing-workflow source have separate recorded commits. The exact
matching Go firmware is independently built in the Go repository. Publication
must validate both producer runs, hashes and provenance. This workflow produces
an artifact only and does not alter stable release classification or publish to
another repository. Hardware and Wi-Fi authentication limitations remain.
