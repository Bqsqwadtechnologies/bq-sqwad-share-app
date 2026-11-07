# BQ Sqwad Share for Windows

This is a native Windows WPF companion for the existing phone **local Wi-Fi sharing bridge**.

## Build
- Windows 10/11
- .NET 8 SDK with Windows Desktop targeting pack

Run from this directory:
`dotnet build BQSqwadShare.Windows/BQSqwadShare.Windows.csproj`

## Connect
1. Start the phone's local sharing server from BQ Sqwad Share.
2. Put the phone and PC on the same trusted Wi-Fi network.
3. Paste the complete QR link shown by the phone, or enter the local address (including port 8787) and six-digit PIN separately.
4. Refresh to list files, download a selected file, or send multiple files to the phone.

The companion never uses a cloud service. The connection is limited to the local network and uses HTTP without transport encryption. Use only a trusted private Wi-Fi network or your own hotspot; never expose the phone sharing port to the public internet.

## Scope
This companion implements local-network file exchange using the phone's existing HTTP bridge. It is not a Windows Nearby Connections implementation and does not yet participate in the Android multi-hop relay mesh.
