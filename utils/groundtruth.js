var fs = require('fs');
var cli = require('cli');
var json2csv = require('json2csv');
var fields = ['topic', 'id'];

var options = cli.parse({
	file: [ 'i', 'file to process', 'file', null]
});

cli.main(function(args,options) {
	if(options.file == null) {
		cli.getUsage();
		exit();
	}
});

var filename = __dirname + '/' + options.file;
var solrDocs = JSON.parse(fs.readFileSync(filename, 'utf8'));

json2csv({ data: solrDocs, fields: fields }, function(err, csv) {
  if (err) console.log(err);
	fs.writeFile('gtt.csv', csv, function(err) {
    if (err) throw err;
    console.log('file saved');
  });
});
