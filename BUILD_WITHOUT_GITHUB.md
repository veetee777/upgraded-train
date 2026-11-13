# Build GLASSFORGE without GitHub

This project includes a Google Colab build notebook (`GLASSFORGE_Build_No_GitHub.ipynb`). Open the notebook in Colab, run its cells in order, upload this source ZIP when prompted, and download the generated debug APK. The notebook installs the Android SDK and Gradle in the temporary Colab session; it does not connect to GitHub.

After installing the APK, open Android Settings → Apps → Default apps → Home app and select GLASSFORGE. Android may show the Home-app chooser the first time you press Home.

The app's custom fonts apply inside GLASSFORGE. Android does not permit an ordinary third-party launcher to replace typography in unrelated apps or system UI.
