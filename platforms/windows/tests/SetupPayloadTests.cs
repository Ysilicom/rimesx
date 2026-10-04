using System;
using System.IO;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;

internal static class SetupPayloadTests
{
    private static int checks;
    private static MemoryStream Archive(params string[] names)
    {
        var stream = new MemoryStream();
        using (var archive = new ZipArchive(stream, ZipArchiveMode.Create, true))
            foreach (var name in names)
                using (var writer = new StreamWriter(archive.CreateEntry(name).Open(), new UTF8Encoding(false)))
                    writer.Write("test content");
        stream.Position = 0;
        return stream;
    }
    private static string Hash(MemoryStream stream)
    {
        using (var sha = SHA256.Create())
        {
            var result = BitConverter.ToString(sha.ComputeHash(stream)).Replace("-", "").ToLowerInvariant();
            stream.Position = 0;
            return result;
        }
    }
    private static void Case(string root, bool valid, bool wrongHash, params string[] names)
    {
        var destination = Path.Combine(root, Guid.NewGuid().ToString("N"));
        using (var stream = Archive(names))
        {
            var rejected = false;
            try { Payload.Extract(stream, wrongHash ? new string('0', 64) : Hash(stream), destination); }
            catch (InvalidDataException) { rejected = true; }
            if (valid == rejected) throw new Exception("Unexpected extraction result: " + String.Join(", ", names));
            if (valid && File.ReadAllText(Path.Combine(destination, names[0])) != "test content")
                throw new Exception("Extracted content changed");
        }
        checks++;
    }
    public static int Main()
    {
        var root = Path.Combine(Path.GetTempPath(), "RIMES setup tests 中文 ' " + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(root);
        try
        {
            Case(root, true, false, "folder/file.txt");
            Case(root, false, true, "file.txt");
            Case(root, false, false, "../escape.txt");
            Case(root, false, false, "folder/../../escape.txt");
            Case(root, false, false, "/absolute.txt");
            Case(root, false, false, "C:/absolute.txt");
            Case(root, false, false, "file.txt:stream");
            Case(root, false, false, "same.txt", "SAME.txt");
            Case(root, false, false, "folder./file.txt");
            Case(root, false, false, "folder /file.txt");
            if (File.Exists(Path.Combine(root, "escape.txt"))) throw new Exception("Extraction escaped its destination");
            Console.WriteLine("PASS: " + checks + " installer payload cases");
            return 0;
        }
        finally { Directory.Delete(root, true); }
    }
}
