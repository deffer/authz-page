
function deleteToken(token){
    var result = window.confirm('Are you sure?');
    console.log("User says "+result+" for "+token);

    if (!result)
        return;

    // ugly piece of code below
    $.ajax({
        url: '/self/token/'+token,
        type: 'DELETE',
        success: function(result) {
            // Do something with the result
            window.location.reload();
        },
        error:function (xhr, ajaxOptions, thrownError){
            if (xhr.status == 200 || xhr.status == 404){
                window.location.reload();
            }else  if (xhr.status == 401 || xhr.status == 403){
                alert("Unauthorized attempt to delete the token");
            } else {
                console.log(xhr.status);
                console.log(xhr.responseText);
                alert("Unexpected error happened");
            }
            //alert(xhr.status);
            //alert(xhr.statusText);
            //alert(xhr.responseText);
        }
    });

}

// jQuery plugin to prevent double submission of forms
// https://stackoverflow.com/questions/2830542/prevent-double-submission-of-forms-in-jquery
jQuery.fn.preventDoubleSubmission = function() {
    //console.log ('adding double-submit prevention to a form');
    $(this).on('submit',function(e){
        var $form = $(this);

        if ($form.data('submitted') === true) {
            // Previously submitted - don't submit again
            e.preventDefault();
        } else {
            // Mark it so that the next submit can be ignored
            // ADDED requirement that form be valid (doesnt work)
            //if($form.valid()) {
                $form.data('submitted', true);
            //}
        }
    });

    // Keep chainability
    return this;
};

$(function() {
    $('form').preventDoubleSubmission();
});