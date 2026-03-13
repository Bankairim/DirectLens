# Privacy Policy for DirectLens

**Last Updated: October 2023**

DirectLens is committed to protecting your privacy. This Privacy Policy explains our practices regarding the collection, use, and disclosure of information when you use our mobile application.

## 1. Zero Data Collection
DirectLens is designed with privacy as a core principle. **We do not collect, store, or transmit any personal data.**
- No account registration is required.
- No personal identifiers (name, email, phone number, etc.) are collected.
- No analytics or tracking SDKs are integrated into the app.

## 2. Use of Accessibility Service
DirectLens requires the activation of an **Accessibility Service** to function. 
- **Purpose:** We use this service solely to detect a long-press gesture on your navigation bar (or designated detection zones) to trigger a screenshot and launch Google Lens.
- **Data Access:** While the Accessibility API technically allows for broader monitoring, DirectLens **only** listens for touch events in the specific zones you define. We do not read your messages, passwords, or any other sensitive information on your screen.

## 3. Screenshot Handling
When you trigger the app:
1. A screenshot of your screen is captured locally.
2. The screenshot is saved temporarily in your device's private cache.
3. The image is immediately passed via a secure Intent to the official **Google Lens** app.
4. **DirectLens does not upload your screenshots to any remote servers.**

## 4. Third-Party Services
Once a screenshot is forwarded to the Google app, the processing of that image is governed by **Google's Privacy Policy** and **Terms of Service**. DirectLens is an independent project and is not affiliated with Google LLC.

## 5. Open Source Transparency
DirectLens is open-source software (licensed under GPLv3). You or any third party can audit our source code on GitHub to verify our privacy claims:
[https://github.com/Bankairim/DirectLens/](https://github.com/Bankairim/DirectLens/)

## 6. Permissions
- **Accessibility Service:** To detect the trigger gesture.
- **Display over other apps:** To show the detection zones (overlay).
- **Vibration:** To provide haptic feedback.
- **Internet:** No internet permission is requested or used by the app itself.

## 7. Changes to This Policy
We may update our Privacy Policy from time to time. Since we do not collect any contact information, we encourage users to check our GitHub repository for any updates.

## 8. Contact
If you have any questions or suggestions about our Privacy Policy, do not hesitate to contact us through our GitHub repository.
