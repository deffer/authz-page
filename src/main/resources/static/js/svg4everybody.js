!function(a,b){"function"==typeof define&&define.amd?
define([],function(){return a.svg4everybody=b()}):"object"==typeof exports?module.exports=b():a.svg4everybody=b()}(this,function(){/*! svg4everybody v2.0.3 | github.com/jonathantneal/svg4everybody */
function a(a,b){
if(b){
var c=document.createDocumentFragment(),d=!a.getAttribute("viewBox")&&b.getAttribute("viewBox");
d&&a.setAttribute("viewBox",d);
for(
var e=b.cloneNode(!0);e.childNodes.length;)c.appendChild(e.firstChild);
a.appendChild(c)}}function b(b){
b.onreadystatechange=function(){
if(4===b.readyState){
var c=b._cachedDocument;
c||(c=b._cachedDocument=document.implementation.createHTMLDocument(""),c.body.innerHTML=b.responseText,b._cachedTarget={}),
b._embeds.splice(0).map(function(d){
var e=b._cachedTarget[d.id];
e||(e=b._cachedTarget[d.id]=c.getElementById(d.id)),
a(d.svg,e)})}},
b.onreadystatechange()}function c(c){function d(){
for(
var c=0;c<l.length;){
var g=l[c],h=g.parentNode;if(h&&/svg/i.test(h.nodeName)){var i=g.getAttribute("xlink:href");if(e&&(!f.validate||f.validate(i,h,g))){
h.removeChild(g);
var m=i.split("#"),n=m.shift(),o=m.join("#");
if(n.length){
var p=j[n];
p||(p=j[n]=new XMLHttpRequest,p.open("GET",n),p.send(),p._embeds=[]),
p._embeds.push({svg:h,id:o}),
b(p)}else
a(h,document.getElementById(o))}}else
++c}
k(d,67)}var e,f=Object(c),g=/\bTrident\/[567]\b|\bMSIE (?:9|10)\.0\b/,h=/\bAppleWebKit\/(\d+)\b/,i=/\bEdge\/12\.(\d+)\b/;e="polyfill"in f?f.polyfill:g.test(navigator.userAgent)||(navigator.userAgent.match(i)||[])[1]<10547||(navigator.userAgent.match(h)||[])[1]<537;
var j={},k=window.requestAnimationFrame||setTimeout,l=document.getElementsByTagName("use");
e&&d()}return c});