using Microsoft.Win32;
using System.Net;
using System.Net.Http;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace BQSqwadShare.Windows;

public sealed class RemoteFile
{
    public string Name { get; init; } = "";
    public string SizeLabel { get; init; } = "";
    public string Url { get; init; } = "";
}

public partial class MainWindow : Window
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromHours(2) };
    private Uri? _baseUri;
    private string _pin = "";

    public MainWindow() => InitializeComponent();

    private bool TryConnect()
    {
        if (!Uri.TryCreate(AddressBox.Text.Trim().TrimEnd('/') + "/", UriKind.Absolute, out var uri) ||
            (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps))
        {
            StatusText.Text = "Enter the local sharing address shown by the phone.";
            return false;
        }
        _baseUri = uri;
        _pin = PinBox.Password.Trim();
        if (_pin.Length < 4) { StatusText.Text = "Enter the phone's sharing PIN."; return false; }
        return true;
    }

    private async void Connect_Click(object sender, RoutedEventArgs e) => await RefreshFiles();
    private async void Refresh_Click(object sender, RoutedEventArgs e) => await RefreshFiles();

    private async Task RefreshFiles()
    {
        if (!TryConnect()) return;
        try
        {
            StatusText.Text = "Connecting to phone…";
            using var response = await _http.GetAsync(new Uri(_baseUri!, "?pin=" + Uri.EscapeDataString(_pin)));
            response.EnsureSuccessStatusCode();
            var html = await response.Content.ReadAsStringAsync();
            var files = new List<RemoteFile>();
            foreach (Match match in Regex.Matches(html, "<li>\\s*<a href=\"(?<url>[^\"]+)\">(?<name>[^<]+)</a>\\s*\\((?<size>[0-9]+) bytes\\)", RegexOptions.IgnoreCase))
            {
                var name = WebUtility.HtmlDecode(match.Groups["name"].Value);
                var link = WebUtility.HtmlDecode(match.Groups["url"].Value);
                var size = long.TryParse(match.Groups["size"].Value, out var n) ? n : 0;
                files.Add(new RemoteFile { Name = name, SizeLabel = $"{size:N0} bytes", Url = new Uri(_baseUri!, link).ToString() });
            }
            FilesList.ItemsSource = files;
            StatusText.Text = $"Connected · {files.Count} shared file(s) available.";
        }
        catch (Exception ex) { StatusText.Text = "Connection failed: " + ex.Message; }
    }

    private async void Upload_Click(object sender, RoutedEventArgs e)
    {
        if (!TryConnect()) return;
        var picker = new OpenFileDialog { Multiselect = true, Title = "Choose files to send to the phone" };
        if (picker.ShowDialog() != true) return;
        try
        {
            foreach (var path in picker.FileNames)
            {
                StatusText.Text = "Sending " + System.IO.Path.GetFileName(path) + "…";
                await using var stream = File.OpenRead(path);
                using var content = new StreamContent(stream);
                content.Headers.ContentLength = stream.Length;
                using var request = new HttpRequestMessage(HttpMethod.Post, new Uri(_baseUri!, "upload?pin=" + Uri.EscapeDataString(_pin))) { Content = content };
                request.Headers.TryAddWithoutValidation("X-File-Name", Uri.EscapeDataString(System.IO.Path.GetFileName(path)));
                using var response = await _http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead);
                response.EnsureSuccessStatusCode();
            }
            StatusText.Text = $"Sent {picker.FileNames.Length} file(s) to the phone.";
        }
        catch (Exception ex) { StatusText.Text = "Send failed: " + ex.Message; }
    }

    private async void Download_Click(object sender, RoutedEventArgs e)
    {
        if (FilesList.SelectedItem is not RemoteFile file) { StatusText.Text = "Select a file first."; return; }
        var picker = new SaveFileDialog { FileName = file.Name, Title = "Save file from phone" };
        if (picker.ShowDialog() != true) return;
        await DownloadFile(file, picker.FileName);
    }

    private async void FilesList_DoubleClick(object sender, MouseButtonEventArgs e) => Download_Click(sender, e);

    private async Task DownloadFile(RemoteFile file, string destination)
    {
        try
        {
            StatusText.Text = "Downloading " + file.Name + "…";
            var url = file.Url + (file.Url.Contains('?') ? "&" : "?") + "pin=" + Uri.EscapeDataString(_pin);
            using var response = await _http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead);
            response.EnsureSuccessStatusCode();
            var temporary = destination + ".part";
            try
            {
                await using var input = await response.Content.ReadAsStreamAsync();
                await using (var output = File.Create(temporary))
                {
                    await input.CopyToAsync(output);
                    await output.FlushAsync();
                    if (response.Content.Headers.ContentLength is long expected && output.Length != expected)
                        throw new IOException("The download was incomplete; the original destination was not replaced.");
                }
                File.Move(temporary, destination, overwrite: true);
                StatusText.Text = "Downloaded " + file.Name;
            }
            catch
            {
                if (File.Exists(temporary)) File.Delete(temporary);
                throw;
            }
        }
        catch (Exception ex) { StatusText.Text = "Download failed: " + ex.Message; }
    }
}
