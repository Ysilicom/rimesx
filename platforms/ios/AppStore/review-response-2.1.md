# App Review response — Guideline 2.1

Use this response only with a screen recording captured on a physical iPhone
running the same build being submitted. Do not submit the placeholder below or
claim that a different build was recorded.

## Required attachment

- [ ] Attach `RIMES-0.1.0-<build>-physical-iPhone-flow.mp4` to App Review.
- [ ] Replace `<build>` below with the submitted build number.
- [ ] Confirm the recording contains no personal text, API key, Apple Account,
  email address, telephone number, or private notification.

## Reply to App Review

Thank you for the opportunity to provide more information about RIMES.

1. We attached a screen recording captured on a physical iPhone running iOS
   `<iOS version>` and RIMES 0.1.0 (`<build>`). It begins by launching the
   containing app, then shows keyboard setup, normal Pinyin input, candidate
   selection, local Buffer editing and insertion. It does not use an account,
   paid feature, or bundled third-party AI credential.

2. RIMES is a consumer Chinese keyboard for iPhone. It helps people type Chinese
   locally using Pinyin, Ziranma double Pinyin, Wubi 86, English, and optional
   two-thumb slide chords. Its Buffer lets a user draft and deliberately insert
   text in blocks. The intended audience is iPhone users who want an offline,
   configurable Chinese input method. The app is free and has no subscription,
   advertising, account creation, or paid content.

3. To access the main features: open RIMES, then open Settings > General >
   Keyboard > Keyboards > Add New Keyboard and add RIMES. Focus a text field and
   select RIMES with the system globe key. Ordinary input and Buffer work with
   Full Access disabled. No login, sample file, or credential is required.

4. Core typing runs on-device using the bundled Rime engine and iOS keyboard
   extension APIs. The containing app can use Apple's on-device Translation
   framework when supported language models have been installed through iOS.
   Optional external AI is not needed for core functionality: a user may choose
   and configure their own HTTPS provider, model, and API key, then explicitly
   send the current Buffer text after recipient consent. RIMES does not operate
   an AI service, bundle a provider credential, process payments, authenticate
   users, or operate a cloud backend.

5. Core offline typing and Buffer behavior are the same in every selected App
   Store region. Apple on-device Translation depends on the device's iOS version,
   supported language models, and the user's downloaded models. Optional AI
   depends on a provider chosen and configured by the user; it is not required to
   use RIMES. Mainland China is not selected for this submission.

6. RIMES is not in a regulated industry. The app includes open-source components
   and language data under their applicable licenses; notices and license texts
   are bundled in the app. The App Store Content Rights declaration accurately
   identifies third-party content. The resource inventory and distribution basis
   are available at `AppStore/RESOURCE_CLEARANCE.md` in the source repository.

Please let us know if you need a different recording angle, a longer feature
demonstration, or any further detail.

## Physical-device recording script

Record only a clean test note such as `你好 RIMES test`; use no personal content.

1. Start iOS screen recording, then launch RIMES from the Home Screen.
2. In RIMES, show the setup instruction and the in-app typing playground.
3. Open Settings > General > Keyboard > Keyboards; add RIMES if it is not already
   enabled, then return to RIMES.
4. Focus the playground, switch with the globe key to RIMES, type `nihao`, and
   choose `你好` from candidates.
5. Open Buffer, type a short test phrase, then demonstrate inserting one block.
6. Leave Full Access disabled. Do not configure external AI in this recording.
7. Return to the app and stop the recording. Before attaching it, review the
   entire video for notifications and private data.

## Operator decision before resubmission

- If the recording is of build 10 and its functionality is current, attach it,
  paste the reply into App Review Notes and reply to Apple, then resubmit.
- If the recording is of build 22 or a newer build, archive/upload that exact
  build, choose it for a new submission, attach the matching recording, and
  submit the updated version. Do not describe build 22 evidence as build 10
  evidence.
