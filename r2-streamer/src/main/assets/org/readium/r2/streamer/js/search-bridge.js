
function getLocatorsUsingRangyFind(searchQuery) {
    console.log("-> getLocatorsUsingRangyFind -> " + searchQuery);

    var rangyRange = rangy.createRange();
    var locators = [];
    var title = parseChapterTitle();
    window.getSelection().removeAllRanges();

    while (rangyRange.findText(searchQuery)) {

        var range = rangyRange.nativeRange;
        var locator = constructSearchLocator(range);
        locator.title = title;
        locators.push(locator);
        rangyRange.collapse(false);
    }

    return locators;
}

function getLocatorsUsingWindowFind(searchQuery) {
    console.log("-> getLocatorsUsingWindowFind -> " + searchQuery);

    var locators = [];
    var title = parseChapterTitle();
    window.getSelection().removeAllRanges();

    while (window.find(searchQuery)) {

        var range = window.getSelection().getRangeAt(0);
        var locator = constructSearchLocator(range);
        locator.title = title;
        locators.push(locator);
    }

    return locators;
}

function constructSearchLocator(range) {

    var cfi = EPUBcfi.Generator.generateRangeComponent(range.startContainer, range.startOffset,
        range.endContainer, range.endOffset);
    cfi = EPUBcfi.Generator.generateCompleteCFI("/0!", cfi);

    var locator = {};
    var locations = {};
    locations.cfi = cfi;
    locator.locations = locations;

    var beforeRange = document.createRange();
    beforeRange.setStart(document.body, 0);
    beforeRange.setEnd(range.startContainer, range.startOffset);

    var afterRange = document.createRange();
    afterRange.setStart(range.endContainer, range.endOffset);
    afterRange.setEnd(document.body, document.body.childNodes.length - 1);

    var offset = 50;
    var text = {};
    text.before = getBeforeCharacters(beforeRange.toString(), offset);
    text.highlight = range.toString();
    text.after = getAfterCharacters(afterRange.toString(), offset);
    locator.text = text;

    return locator;
}

function getBeforeCharacters(before, offset) {

    var result = before.slice(-offset);
    if (offset < before.length)
        result = "..." + result;
    result = result.replace(/(\r\n|\n|\r)/gm, " ");
    result = result.replace(/\s+/g, " ");
    return result;
}

function getAfterCharacters(after, offset) {

    var result = after.slice(0, offset);
    if (offset < after.length)
        result += "...";
    result = result.replace(/(\r\n|\n|\r)/gm, " ");
    result = result.replace(/\s+/g, " ");
    return result;
}

// To be replaced with r2-js parseChapterTitle() logic if any
function parseChapterTitle() {

    var element;
    element = document.querySelectorAll("section[title]")[0];
    if (element)
        return element.getAttribute("title");

    element = document.querySelector("h1");
    if (element)
        return element.textContent;

    element = document.querySelector("h2");
    if (element)
        return element.textContent;
}
