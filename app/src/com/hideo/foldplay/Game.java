package com.hideo.foldplay;

final class Game {
    final String title;
    final SystemInfo system;
    /** URI SAF du fichier (content://...). */
    final String uri;
    /** Chemin disque réel quand on sait le déduire (utile pour RetroArch), sinon null. */
    final String path;
    final String mime;
    final String baseName;
    /** Nom du fichier sans extension, casse d'origine (souvent un nom No-Intro). */
    final String fileBase;
    /** Nom complet du fichier, extension comprise. */
    final String fileName;
    String coverUri;
    /** Taille du fichier en octets, -1 si inconnue. */
    long size = -1;
    /** Texte de la fiche pour une application ou une action (pas de fiche Wikipédia à chercher). */
    String note;
    /** Section de Remote Play (« ps », « xbox », « pc »), sinon null. */
    String group;

    /** Application du téléphone (« app: »), client à installer (« play: ») ou action (« action: »). */
    Game(String title, SystemInfo system, String uri) {
        this.title = title;
        this.system = system;
        this.uri = uri;
        this.path = null;
        this.mime = null;
        this.fileName = title;
        this.fileBase = title;
        this.baseName = title.toLowerCase();
    }

    /** Vrai pour ce qui n'est pas un fichier de jeu : application, client à installer, action. */
    boolean isApp() {
        return uri.startsWith("app:") || uri.startsWith("play:") || uri.startsWith("action:");
    }

    Game(String fileName, SystemInfo system, String uri, String path, String mime) {
        this.title = cleanTitle(fileName);
        this.system = system;
        this.uri = uri;
        this.path = path;
        this.mime = mime;
        this.fileName = fileName;
        int dot = fileName.lastIndexOf('.');
        this.fileBase = dot > 0 ? fileName.substring(0, dot) : fileName;
        this.baseName = fileBase.toLowerCase();
    }

    /** "Super Game (USA) [!].sfc" devient "Super Game". */
    static String cleanTitle(String fileName) {
        String t = fileName;
        int dot = t.lastIndexOf('.');
        if (dot > 0) t = t.substring(0, dot);
        t = t.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "");
        t = t.replace('_', ' ').trim();
        return t.isEmpty() ? fileName : t;
    }
}
