package com.mtgtrader

import com.mtgtrader.data.NextcloudClient
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reading Nextcloud's folder listings (WebDAV PROPFIND) for the sync folder picker. */
class NextcloudFolderTest {
    private val nextcloud = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
 <d:response><d:href>/remote.php/dav/files/joel/Games/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
 <d:response><d:href>/remote.php/dav/files/joel/Games/Magic%20%26%20Co/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
 <d:response><d:href>/remote.php/dav/files/joel/Games/C%2B%2B/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
 <d:response><d:href>/remote.php/dav/files/joel/Games/sync.json.gz</d:href><d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
 <d:response><d:href>/remote.php/dav/files/joel/Games/notes.txt</d:href><d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
</d:multistatus>"""

    @Test
    fun listsSubfoldersAndSpotsTheSyncFile() {
        val l = NextcloudClient.parseListing(nextcloud, "/remote.php/dav/files/joel/Games")
        assertEquals(listOf("C++", "Magic & Co"), l.folders)
        assertEquals(true, l.hasSyncFile)
    }

    @Test
    fun otherPrefixesAndFullUrls() {
        val xml = """<D:multistatus xmlns:D="DAV:"><D:response><D:href>https://cloud.example.com/remote.php/dav/files/joel/</D:href>
            <D:propstat><D:prop><D:resourcetype><D:collection /></D:resourcetype></D:prop></D:propstat></D:response>
            <D:response><D:href>https://cloud.example.com/remote.php/dav/files/joel/MTG%20Trader/</D:href>
            <D:propstat><D:prop><D:resourcetype><D:collection /></D:resourcetype></D:prop></D:propstat></D:response></D:multistatus>"""
        val l = NextcloudClient.parseListing(xml, "/remote.php/dav/files/joel/")
        assertEquals(listOf("MTG Trader"), l.folders)
        assertEquals(false, l.hasSyncFile)
    }
}
