import XCTest
@testable import RimesCore

final class CoreTests: XCTestCase {
    func testEveryBundledMappingIsReachableAndResolvesIdentically() throws {
        let p = try ChordProfile.builtIn.validated()
        XCTAssertEqual(p.mappings.count, 427)
        for entry in p.mappings {
            let keys = Set(entry.keys)
            let expected = try XCTUnwrap(p.resolve(keys), entry.keys)
            XCTAssertEqual(expected.preview,entry.output)
            let separator = p.boundaryPolicy == .legacyBatches || entry.kind == .syllable ? "'" : ""
            XCTAssertEqual(expected.input, entry.output + separator, entry.keys)
            for reverse in [false,true] {
                var g = ChordGesture(); var strokes: [(Int,[Character])] = []
                for (i,hand) in Hand.allCases.enumerated() {
                    let chars = Array(keys.filter { p.hand(for:$0) == hand }).sorted()
                    if !chars.isEmpty { strokes.append((i,chars)); g.begin(id:i,key:chars[0],profile:p); g.move(id:i,key:chars.last,profile:p) }
                }
                XCTAssertEqual(g.keys,keys)
                let ordered = reverse ? strokes.reversed().map { $0 } : strokes
                var outcomes = [ChordResolution]()
                for (id,chars) in ordered { if let result = g.end(id:id,key:chars.last,profile:p) { outcomes.append(result) } }
                XCTAssertEqual(outcomes,[expected],entry.keys)
                XCTAssertNil(g.end(id:0,key:"a",profile:p))
            }
        }
        var z = p.copy(); z.outputEncoding = .ziranma
        _ = try z.validated()
        for m in z.mappings { XCTAssertEqual(z.resolve(Set(m.keys))?.input,z.encoded(m)) }
    }
    func testStartAndEndOnly() {
        let p = ChordProfile.builtIn; var g = ChordGesture()
        g.begin(id:1,key:"a",profile:p); g.move(id:1,key:"s",profile:p); g.move(id:1,key:"d",profile:p)
        XCTAssertEqual(g.keys,Set("ad"))
        g.move(id:1,key:"a",profile:p); XCTAssertEqual(g.keys,Set("a"))
        XCTAssertEqual(g.end(id:1,key:"a",profile:p)?.input,"a")
    }
    func testWholeMappingWinsBeforeLegalLeftRightComposition() throws {
        var p = ChordProfile.builtIn.copy(); p.boundaryPolicy = .explicitSyllables
        p.mappings = [.init(keys:"sd",output:"sh",kind:.fragment), .init(keys:"jk",output:"ang",kind:.fragment), .init(keys:"sdjk",output:"chang",kind:.syllable)]
        _ = try p.validated()
        XCTAssertEqual(p.resolve(Set("sdjk"))?.input,"chang'")
        p.mappings.removeLast()
        XCTAssertEqual(p.resolve(Set("sdjk"))?.input,"shang'")
        XCTAssertEqual(p.resolve(Set("sd"))?.input,"sh")
        XCTAssertEqual(p.resolve(Set("bi"))?.input,"bi'")
        XCTAssertNil(p.resolve(Set("qjk"))) // q + ang is not a legal syllable.
        p.outputEncoding = .ziranma
        XCTAssertEqual(p.resolve(Set("sdjk"))?.input,"uh")
        XCTAssertEqual(p.resolve(Set("sd"))?.input,"u")
        XCTAssertEqual(p.resolve(Set("j"))?.input,"j")
    }
    func testAssociationIndexLooksUpSortedLinesExactly() {
        let lines = ["中国\t人\t队", "你好\t啊\t吗", "字\t母\t典", "谢谢\t了\t大家"]
            .sorted { Array($0.split(separator: "\t")[0].utf8).lexicographicallyPrecedes(Array($1.split(separator: "\t")[0].utf8)) }
        let index = AssociationIndex(data: Data((lines.joined(separator: "\n") + "\n").utf8))
        XCTAssertEqual(index.count, 4)
        XCTAssertEqual(index.continuations(for: "谢谢"), ["了", "大家"])
        XCTAssertEqual(index.continuations(for: "字"), ["母", "典"])
        XCTAssertEqual(index.continuations(for: "谢"), []) // exact word only, never a prefix match
        XCTAssertEqual(index.continuations(for: ""), [])
        XCTAssertEqual(AssociationIndex(data: Data()).continuations(for: "你好"), [])
    }
    func testAssociationSuggestionsPreferLearnedWordsAndFallBackToShorterKeys() {
        let index = AssociationIndex(data: Data("好\t的\t像\n谢谢\t了\t大家\n".utf8))
        var history = AssociationHistory()
        XCTAssertEqual(Associations.suggestions(after: "谢谢", index: index, history: history), ["了", "大家"])
        history.record(previous: "谢谢", next: "你"); history.record(previous: "谢谢", next: "大家"); history.record(previous: "谢谢", next: "大家")
        XCTAssertEqual(Associations.suggestions(after: "谢谢", index: index, history: history), ["大家", "你", "了"]) // learned first, deduplicated
        XCTAssertEqual(Associations.suggestions(after: "非常好", index: index, history: history), ["的", "像"]) // last character
        XCTAssertEqual(Associations.suggestions(after: "hello", index: index, history: history), [])
        XCTAssertEqual(Associations.suggestions(after: "说谢谢", index: index, history: history).first, "大家") // last two characters
        history.record(previous: "hello", next: "你"); history.record(previous: "你", next: "!")
        XCTAssertTrue(history.next(after: "hello").isEmpty); XCTAssertTrue(history.next(after: "你").isEmpty) // Chinese only
    }
    func testAssociationHistoryStaysBounded() throws {
        var history = AssociationHistory()
        for i in 0..<(AssociationHistory.maxWords + 50) {
            let word = String(UnicodeScalar(0x4E00 + i)!)
            history.record(previous: word, next: "的")
        }
        for i in 0..<20 { history.record(previous: "我", next: String(UnicodeScalar(0x5000 + i)!)) }
        XCTAssertLessThanOrEqual(history.next(after: "我").count, AssociationHistory.maxNextPerWord)
        XCTAssertTrue(history.next(after: String(UnicodeScalar(0x4E00)!)).isEmpty) // oldest word evicted
        let data = try JSONEncoder().encode(history)
        XCTAssertEqual(try JSONDecoder().decode(AssociationHistory.self, from: data), history)
    }
    func testBufferBlocksFollowCommitsLikeTheDesktop() {
        var b = BufferSession()
        b.insert("你好"); XCTAssertEqual(b.blocks, ["你好"]) // one word, one block
        b.insert("世界"); b.insert("，"); XCTAssertEqual(b.blocks, ["你好", "世界，"]) // punctuation joins the word before
        for c in "hello" { b.insert(String(c)) }
        b.insert(" "); b.insert("😀")
        XCTAssertEqual(b.blocks, ["你好", "世界，", "hello ", "😀"]) // letters form one word; emoji stands alone
        XCTAssertEqual(b.blocks.joined(), b.source)
        // Inserting inside a block splits it around the new commit.
        b.edit("你好世界", cursor: 2); XCTAssertEqual(b.blocks, ["你好世界"]) // whole text: clause fallback
        b.insert("的"); XCTAssertEqual(b.blocks, ["你好", "的", "世界"])
        // Deleting a whole block removes its boundary; partial deletion keeps the rest.
        b.backspace(); XCTAssertEqual(b.blocks, ["你好", "世界"])
        b.moveCursor(1); b.backspace(); XCTAssertEqual(b.blocks, ["你好", "界"])
        // Selection spanning blocks collapses them.
        var c = BufferSession(); ["我们", "今天", "去", "公园"].forEach { c.insert($0) }
        c.moveCursor(-4); c.beginSelection(); c.extendSelection(-2) // select 们今
        c.backspace(); XCTAssertEqual(c.blocks, ["我", "天", "去", "公园"])
        // Delivery consumes exactly the head block and keeps the rest intact.
        var d = BufferSession(); ["谢谢", "大家", "。"].forEach { d.insert($0) }
        XCTAssertEqual(d.pending, ["谢谢", "大家。"])
        d.consumed(all: false); XCTAssertEqual(d.blocks, ["大家。"]); XCTAssertEqual(d.cursor, 3)
        d.consumed(all: true); XCTAssertEqual(d.source, ""); XCTAssertEqual(d.blocks, [])
    }
    func testBlockDeleteRemovesTheWholeBlockBeforeTheCursor() {
        var b = BufferSession(); ["我们", "今天", "去", "公园", "。"].forEach { b.insert($0) }
        XCTAssertEqual(b.blocks, ["我们", "今天", "去", "公园。"])
        b.deleteBlockBackward(); XCTAssertEqual(b.blocks, ["我们", "今天", "去"]); XCTAssertEqual(b.cursor, 5)
        b.moveCursor(-2) // inside 今天: the whole block goes, including text after the cursor
        b.deleteBlockBackward(); XCTAssertEqual(b.blocks, ["我们", "去"]); XCTAssertEqual(b.cursor, 2)
        b.moveCursor(-2); b.deleteBlockBackward(); XCTAssertEqual(b.blocks, ["我们", "去"]) // nothing before the cursor
        b.moveCursor(3); b.beginSelection(); b.extendSelection(-1) // select 去: a selection wins over the block rule
        b.deleteBlockBackward(); XCTAssertEqual(b.source, "我们")
        b.deleteBlockBackward(); b.deleteBlockBackward(); XCTAssertEqual(b.source, "")
    }
    func testBufferSelectionExtendsFromAnchorAndDeleteRemovesIt() {
        var b = BufferSession(); b.edit("ab😀cdef", cursor: 2)
        b.beginSelection(); XCTAssertNil(b.selection) // empty until the cursor moves
        b.extendSelection(3); XCTAssertEqual(b.selection, 2..<5); XCTAssertEqual(b.cursor, 5)
        b.extendSelection(-4); XCTAssertEqual(b.selection, 1..<2) // crosses the anchor
        b.extendSelection(-9); XCTAssertEqual(b.selection, 0..<2); XCTAssertEqual(b.cursor, 0)
        b.extendSelection(4); XCTAssertEqual(b.selection, 2..<4)
        b.backspace(); XCTAssertEqual(b.source, "abdef"); XCTAssertEqual(b.cursor, 2); XCTAssertNil(b.selection)
        b.backspace(); XCTAssertEqual(b.source, "adef") // then ordinary deletion
        b.beginSelection(); b.extendSelection(2); b.moveCursor(1)
        XCTAssertNil(b.selection); XCTAssertEqual(b.cursor, 4) // a plain move clears
        b.moveCursor(-2); b.beginSelection(); b.extendSelection(1); b.insert("X")
        XCTAssertNil(b.selection); XCTAssertEqual(b.source, "adeXf") // typing inserts at the cursor, keeps text
        b.beginSelection(); b.extendSelection(-1); b.clearSelection(); XCTAssertNil(b.selection)
    }
    func testHandPreviewShowsEachHandsMappingAndLiveCombination() throws {
        var p = ChordProfile.builtIn.copy(); p.boundaryPolicy = .explicitSyllables
        p.mappings = [.init(keys:"sd",output:"sh",kind:.fragment), .init(keys:"jk",output:"ang",kind:.fragment)]
        _ = try p.validated()
        var g = ChordGesture()
        XCTAssertNil(g.handPreview(in: p))
        g.begin(id: 1, key: "s", profile: p)
        var preview = try XCTUnwrap(g.handPreview(in: p))
        XCTAssertEqual(preview.left, .init(keys: "s", output: "s")); XCTAssertNil(preview.right)
        g.move(id: 1, key: "d", profile: p)
        g.begin(id: 2, key: "j", profile: p)
        preview = try XCTUnwrap(g.handPreview(in: p))
        XCTAssertEqual(preview.left?.output, "sh"); XCTAssertEqual(preview.right, .init(keys: "j", output: "j"))
        g.move(id: 2, key: "k", profile: p)
        preview = try XCTUnwrap(g.handPreview(in: p))
        XCTAssertEqual(preview.left?.keys, "sd"); XCTAssertEqual(preview.right?.output, "ang")
        XCTAssertEqual(preview.combined, "shang")
        // Display never changes what release commits.
        XCTAssertEqual(preview.combined, g.resolution(in: p)?.preview)
        _ = g.end(id: 1, key: "d", profile: p)
        XCTAssertEqual(g.handPreview(in: p)?.combined, "shang") // other thumb still down
        XCTAssertEqual(g.end(id: 2, key: "k", profile: p)?.input, "shang'")
        XCTAssertNil(g.handPreview(in: p))
        g.begin(id: 3, key: "s", profile: p); g.cancel()
        XCTAssertNil(g.handPreview(in: p))
    }
    func testEmptySpaceKeepsEndpointAndEitherReleaseOrderCommitsOnce() {
        let p = ChordProfile.builtIn
        for reverse in [false, true] {
            var g = ChordGesture()
            g.begin(id: 1, key: "d", profile: p); g.move(id: 1, key: "v", profile: p)
            g.begin(id: 2, key: "k", profile: p); g.move(id: 2, key: "m", profile: p)
            let expected = p.resolve(Set("dvkm")); XCTAssertNotNil(expected)
            g.move(id: 1, key: nil, profile: p); g.move(id: 2, key: nil, profile: p)
            XCTAssertEqual(g.keys, Set("dvkm"))
            g.move(id: 1, key: "k", profile: p) // Crossing into the other hand is not an endpoint.
            XCTAssertEqual(g.keys, Set("dvkm"))
            g.move(id: 1, key: "d", profile: p); XCTAssertEqual(g.keys, Set("dkm"))
            g.move(id: 1, key: "v", profile: p)
            XCTAssertNil(g.end(id: reverse ? 2 : 1, key: nil, profile: p))
            XCTAssertEqual(g.end(id: reverse ? 1 : 2, key: nil, profile: p), expected)
            XCTAssertNil(g.end(id: 1, key: nil, profile: p))
        }
        var g = ChordGesture()
        g.begin(id: 1, key: "a", profile: p); g.move(id: 1, key: nil, profile: p)
        g.cancel(); XCTAssertNil(g.end(id: 1, key: nil, profile: p))
        g.begin(id: 2, key: "a", profile: p); g.reset()
        XCTAssertNil(g.end(id: 2, key: nil, profile: p))
    }
    func testExtraFingerQuarantinesUntilAllLift() {
        let p = ChordProfile.builtIn; var g = ChordGesture()
        g.begin(id:1,key:"a",profile:p); g.begin(id:2,key:"j",profile:p); g.begin(id:3,key:"s",profile:p)
        XCTAssertTrue(g.cancelled)
        for id in [1,2,3] { XCTAssertNil(g.end(id:id,key:"a",profile:p)) }
        g.begin(id:4,key:"a",profile:p); XCTAssertEqual(g.end(id:4,key:"a",profile:p)?.input,"a")
    }
    func testReleasedHandCannotRestartSameGroup() {
        let p = ChordProfile.builtIn; var g = ChordGesture()
        g.begin(id:1,key:"a",profile:p); g.begin(id:2,key:"j",profile:p)
        XCTAssertNil(g.end(id:1,key:"a",profile:p)); g.begin(id:3,key:"s",profile:p)
        XCTAssertNil(g.end(id:2,key:"j",profile:p)); XCTAssertNil(g.end(id:3,key:"s",profile:p))
    }
    func testCancelledGestureNeverCommits() {
        let p = ChordProfile.builtIn; var g = ChordGesture()
        g.begin(id:1,key:"a",profile:p); g.cancel(); XCTAssertNil(g.end(id:1,key:"a",profile:p))
    }
    func testProfileRejectsNativeAndUnreachableAndDuplicate() throws {
        var p = ChordProfile.builtIn.copy(); p.nativeSchemeID = "yoyo"; XCTAssertThrowsError(try p.validated())
        p.nativeSchemeID = nil; p.mappings.append(.init(keys:"asd",output:"hao",kind:.syllable)); XCTAssertThrowsError(try p.validated())
        p.mappings.removeLast(); p.mappings.append(p.mappings[0]); XCTAssertThrowsError(try p.validated())
        let imported = try ChordProfile.imported(JSONEncoder().encode(ChordProfile.builtIn)); XCTAssertNotEqual(imported.id,ChordProfile.builtIn.id)
    }
    func testBufferEditingInvalidatesLateResponseAndNeverLosesSource() {
        var b = BufferSession(); b.edit("hello")
        let id = b.begin(); b.receive("Bonjour",id:id); XCTAssertTrue(b.generating)
        b.insert(" world"); b.finish("Bonjour",id:id)
        XCTAssertEqual(b.source,"hello world"); XCTAssertNil(b.result); XCTAssertFalse(b.generating)
        b.moveCursor(-6); b.insert("!"); XCTAssertEqual(b.source,"hello! world")
        b.backspace(); XCTAssertEqual(b.source,"hello world")
    }
    func testOrderedDeliveryPreservesAllCharactersAndPartialRemainder() {
        let text = "你好。 下一句！\nThird 👩🏽‍💻"
        XCTAssertEqual(TextBlocks.split(text).joined(),text)
        var b = BufferSession(); b.edit(text)
        XCTAssertEqual(b.pending.first, "你好。 ") // Desktop keeps trailing whitespace with its clause.
        b.consumed(all:false)
        XCTAssertEqual(b.source,"下一句！\nThird 👩🏽‍💻")
        let id = b.begin(); b.finish("One! Two?",id:id); b.consumed(all:false)
        XCTAssertEqual(b.pending,[" Two?"])
        b.cancel() // A same-session target change cancels requests, not pending text.
        XCTAssertEqual(b.pending,[" Two?"]); b.consumed(all:true); XCTAssertEqual(b.source,"")
    }
    func testPoemInstructionFollowsOptions() throws {
        var options = PoemOptions(); options.mode = .acrosticHead; options.lineLength = .five
        let card = PoemWordCard(name: "春", words: ["杏花", "细雨"])
        var library = PoemLibrary(cards: [card]); options.cardIDs = [card.id]
        let head = try PoemPrompt.instruction(source: "春 眠，不觉", options: options, library: library)
        XCTAssertTrue(head.contains("「春眠不觉」这 4 个字作为每一句的第一个字"))
        XCTAssertTrue(head.contains("每句 5 个字")); XCTAssertTrue(head.contains("杏花、细雨"))
        options.mode = .acrosticTail
        XCTAssertTrue(try PoemPrompt.instruction(source: "春眠", options: options, library: library).contains("最后一个字"))
        XCTAssertThrowsError(try PoemPrompt.instruction(source: "，。 ", options: options, library: library)) { XCTAssertEqual($0 as? PoemError, .noHiddenCharacters) }
        XCTAssertThrowsError(try PoemPrompt.instruction(source: String(repeating: "字", count: 17), options: options, library: library))
        // Improvised poems take their line count from the pattern; a missing pattern falls back to the first built-in.
        options.mode = .improvise; options.lineLength = .free; options.patternID = "builtin.lushi"
        let lushi = try PoemPrompt.instruction(source: "秋夜", options: options, library: library)
        XCTAssertTrue(lushi.contains("全诗共 8 句")); XCTAssertFalse(lushi.contains("个字（不计标点）"))
        library.patterns = [PoemPattern(id: "mine", name: "我的", instruction: "每句以你结尾")]
        options.patternID = "mine"
        XCTAssertTrue(try PoemPrompt.instruction(source: "秋夜", options: options, library: library).contains("每句以你结尾"))
        let decoded = try JSONDecoder().decode(KeyboardPreferences.self, from: Data(#"{"scheme":"pinyin"}"#.utf8))
        XCTAssertEqual(decoded.poem, PoemOptions())
    }
    func testEndpointConsentAndBody() throws {
        let p = ProviderConfiguration(name:"Test",baseURL:"https://example.test/v1",model:"test")
        XCTAssertThrowsError(try AIRequest.make(provider:p,key:"secret",source:"only me",instruction:AIPrompt.polish,consent:""))
        let req = try AIRequest.make(provider:p,key:"secret",source:"only me",instruction:AIPrompt.polish,consent:p.consentIdentity)
        XCTAssertEqual(req.url?.path,"/v1/chat/completions")
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with:XCTUnwrap(req.httpBody)) as? [String:Any])
        XCTAssertEqual((json["messages"] as? [[String:String]])?.last?["content"],"only me")
        XCTAssertFalse(String(data:try JSONEncoder().encode(p),encoding:.utf8)!.contains("secret"))
        for url in ["http://a.test", "https://user:password@a.test", "https://a.test?q=key", "https://a.test/#key"] {
            XCTAssertThrowsError(try ProviderConfiguration(baseURL:url).endpoint("models"))
        }
    }
    func testSSEEveryByteBoundaryAndTruncation() throws {
        let stream = "data: {\"choices\":[{\"delta\":{\"content\":\"你好👩🏽‍💻\"},\"finish_reason\":null}]}\r\n\r\ndata: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
        var d = SSETextDecoder()
        for byte in stream.utf8 { try d.append(Data([byte])) }
        XCTAssertEqual(try d.complete(),"你好👩🏽‍💻")
        var partial = SSETextDecoder(); try partial.append(Data("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n".utf8)); XCTAssertThrowsError(try partial.complete())
        var length = SSETextDecoder(); XCTAssertThrowsError(try length.append(Data("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n".utf8)))
    }
    func testSSESeparatesThinkingFromAnswer() throws {
        func event(_ delta: String, finish: String = "null") -> Data { Data("data: {\"choices\":[{\"delta\":\(delta),\"finish_reason\":\(finish)}]}\n\n".utf8) }
        var fields = SSETextDecoder()
        try fields.append(event(#"{"reasoning_content":"想一想"}"#)); XCTAssertEqual(fields.reasoning, "想一想"); XCTAssertEqual(fields.text, "")
        try fields.append(event(#"{"content":"春眠"}"#, finish: #""stop""#)); try fields.append(Data("data: [DONE]\n\n".utf8))
        XCTAssertEqual(try fields.complete(), "春眠")
        var inline = SSETextDecoder()
        try inline.append(event(#"{"content":"<think>先构思"}"#)); XCTAssertEqual(inline.text, ""); XCTAssertEqual(inline.reasoning, "先构思")
        try inline.append(event(#"{"content":"</think>\n春风"}"#, finish: #""stop""#)); try inline.append(Data("data: [DONE]\n\n".utf8))
        XCTAssertEqual(try inline.complete(), "春风")
    }
    func testReasoningEffortIsOptional() throws {
        let p = ProviderConfiguration(name:"Test",baseURL:"https://example.test/v1",model:"test")
        func body(_ effort: AIReasoningEffort) throws -> [String: Any] {
            let req = try AIRequest.make(provider:p,key:"k",source:"s",instruction:"i",consent:p.consentIdentity,reasoning:effort)
            return try XCTUnwrap(JSONSerialization.jsonObject(with:XCTUnwrap(req.httpBody)) as? [String:Any])
        }
        XCTAssertEqual(try body(.minimal)["reasoning_effort"] as? String, "minimal")
        XCTAssertNil(try body(.unspecified)["reasoning_effort"])
    }
    func testLineBreaksStayWithTheirBlock() {
        XCTAssertEqual(TextBlocks.split("春眠不觉晓，\n处处闻啼鸟。\n夜来风雨声，\n花落知多少。"),
                       ["春眠不觉晓，\n", "处处闻啼鸟。\n", "夜来风雨声，\n", "花落知多少。"])
        XCTAssertEqual(TextBlocks.split("他说：“好。”\r\n\n然后！？再见"), ["他说：“好。”\r\n\n", "然后！？", "再见"])
        XCTAssertEqual(TextBlocks.split("One! Two?"), ["One!", " Two?"])
        XCTAssertFalse(TextBlocks.split("甲。\n\n乙\n").contains { $0.allSatisfy(\.isNewline) })
    }
    func testDefaultChordReachesNiang() {
        // uo alone is the fragment "uang"; n + uang is not a syllable, so niang needs its own chord.
        XCTAssertEqual(ChordProfile.builtIn.resolve(Set("dvuo"))?.preview, "niang")
        XCTAssertEqual(ChordProfile.builtIn.resolve(Set("sduo"))?.preview, "liang")
    }
    func testPluginBlocksAreKeptAsGiven() {
        var b = BufferSession(); b.edit("今天天气很好，我们去公园吧。明天见")
        XCTAssertEqual(DefaultBlockSegmenter.segments(from: b.source).joined(), b.source)
        XCTAssertGreaterThan(DefaultBlockSegmenter.segments(from: b.source).count, 1)
        let id = b.begin(); b.finish("The weather is nice today, let's go to the park. See you tomorrow", id: id,
                                     blocks: ["The weather is nice today, ", "let's go to the park. ", "", "See you tomorrow"])
        XCTAssertEqual(b.pluginPending, ["The weather is nice today, ", "let's go to the park. ", "See you tomorrow"])
        b.consumePlugin(all: false); XCTAssertEqual(b.pluginPending, ["let's go to the park. ", "See you tomorrow"])
        let prefs = try? JSONDecoder().decode(KeyboardPreferences.self, from: Data(#"{"scheme":"pinyin"}"#.utf8))
        XCTAssertEqual(prefs?.speakTranslation, false)
    }
    func testTextArtAlwaysKeepsItsFrame() {
        var options = TextArtOptions(); options.width = 4; options.height = 3
        // Chatter, a code fence, a too-long row, a short row and a foreign emoji are all forced onto the grid.
        let reply = "Here is a cat:\n```\n🟧🟧🟧🟧🟧🟧\n⬛🟧\n🟧🐱🟧⬛\n```"
        let rows = TextArt.normalize(reply, options: options)
        XCTAssertEqual(rows, ["🟧🟧🟧🟧", "⬛🟧⬜⬜", "🟧🟧⬛⬜"])
        XCTAssertTrue(rows.allSatisfy { $0.count == 4 })
        XCTAssertEqual(TextArt.normalize("🟥", options: options), ["⬜⬜⬜⬜", "🟥⬜⬜⬜", "⬜⬜⬜⬜"]) // centred vertically
        // Any characters are allowed; the frame is still forced by character count.
        options.style = .hanzi
        let free = TextArt.normalize("这是一只猫：\n```\n田 口A★🐱\n一", options: options)
        XCTAssertTrue(free.allSatisfy { $0.count == 4 })
        XCTAssertEqual(free[0], "田 口A")
        XCTAssertEqual(free[1], "一\u{3000}\u{3000}\u{3000}")
        XCTAssertEqual(TextArt.normalize("★🐱、〇", options: options)[1], "★🐱、〇")
        XCTAssertTrue(TextArt.instruction(options: options).contains("正好 3 行，每行正好 4 格"))
    }
    func testRedirectNeverCarriesCredentials() {
        let delegate = NoRedirectSessionDelegate()
        let session = URLSession(configuration:.ephemeral), request = URLRequest(url:URL(string:"https://elsewhere.test")!)
        let task = session.dataTask(with:request)
        let response = HTTPURLResponse(url:URL(string:"https://original.test")!,statusCode:302,httpVersion:nil,headerFields:nil)!
        var called = false
        delegate.urlSession(session,task:task,willPerformHTTPRedirection:response,newRequest:request) { redirect in called = true; XCTAssertNil(redirect) }
        XCTAssertTrue(called); session.invalidateAndCancel()
    }
    func testSSEBoundsMultilineEventsWithoutBlankSeparator() throws {
        var decoder = SSETextDecoder()
        let line = Data(("data: " + String(repeating: "x", count: 4096) + "\n").utf8)
        for _ in 0..<255 { try decoder.append(line) }
        XCTAssertThrowsError(try decoder.append(line)) { error in
            guard case CoreError.tooLarge = error else { return XCTFail("Unexpected error: \(error)") }
        }
    }
}

private final class MockAIProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "fixture.invalid" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let failing = request.url!.path.contains("error")
        client?.urlProtocol(self,didReceive:HTTPURLResponse(url:request.url!,statusCode:failing ? 429 : 200,httpVersion:"HTTP/1.1",headerFields:["Content-Type":"text/event-stream"])!,cacheStoragePolicy:.notAllowed)
        let body = failing ? "sensitive upstream details must not appear" : "data: {\"choices\":[{\"delta\":{\"content\":\"Success\"},\"finish_reason\":null}]}\n\ndata: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
        client?.urlProtocol(self,didLoad:Data(body.utf8)); client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
extension CoreTests {
    func testActualStreamingTransportAndHTTPFailureAreBoundedAndRedacted() async throws {
        let client = AIClient { let c = URLSessionConfiguration.ephemeral; c.protocolClasses = [MockAIProtocol.self]; return c }
        let success = URLRequest(url:URL(string:"https://fixture.invalid/success")!)
        let text = try await client.generate(success) { _, _ in }
        XCTAssertEqual(text,"Success")
        do { _ = try await client.generate(URLRequest(url:URL(string:"https://fixture.invalid/error")!)) { _, _ in }; XCTFail("Expected HTTP failure") }
        catch let CoreError.response(code) { XCTAssertEqual(code,429) }
        catch { XCTFail("Unexpected error: \(error)") }
    }
}
